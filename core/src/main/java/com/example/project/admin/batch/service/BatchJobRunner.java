package com.example.project.admin.batch.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 관리자 화면에서 누른 배치를 실제로 돌린다.
 *
 * <p><b>배치 빈을 core 컨텍스트에 섞지 않고, 실행할 때만 자식 컨텍스트를 띄웠다가 닫는다.</b>
 * {@code @EnableBatchProcessing} 이 끌고 오는 SimpleBatchConfiguration 에는 {@code transactionManager}
 * 라는 이름의 빈이 있어 core 의 RootConfig 와 이름이 겹치고, RootConfig 의
 * {@code @ComponentScan("com.example.project")} 가 batch 의 @Component/@Mapper 까지 주워 가는데
 * 매퍼 XML 은 core 디렉터리만 훑어서 짝이 맞지 않는다. 컨텍스트를 나누면 이 문제가 전부 사라지고,
 * 배치가 쓰는 커넥션 풀도 실행하는 동안만 살아 있다.
 *
 * <p>컨텍스트 기동에 1~2초가 붙지만 수동 실행은 비동기라 화면이 기다리지 않는다.
 */
@Log4j2
@Component
public class BatchJobRunner {

    private static final String BATCH_CONFIG_CLASS = "com.example.project.batch.config.BatchConfig";

    /**
     * 실행을 한 줄로 세운다. 배치가 같은 테이블을 건드리는 데다, 관리자가 여러 잡을 동시에
     * 돌릴 이유도 없다. 대기 중인 잡도 running 으로 보이는데 지금은 잡이 하나뿐이라 문제되지 않는다.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "admin-batch-runner");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    private final Path workDir;

    /**
     * application.properties 는 각자 로컬 파일이라 이 키가 없는 팀원이 있을 수 있다.
     * 기본값이 없으면 자리표시자 치환에 실패해 톰캣이 아예 못 뜬다.
     */
    public BatchJobRunner(@Value("${law.batch.work-dir:${java.io.tmpdir}/law-batch}") String workDir) {
        this.workDir = Path.of(workDir).toAbsolutePath().normalize();
    }

    public boolean isRunning(String jobName) {
        return runningJobs.contains(jobName);
    }

    /**
     * 새 실행. JobParameters 에 시각을 넣어 매번 새 JobInstance 로 돌린다(cron 실행과 같은 방식).
     *
     * @return 접수했으면 true, 이미 돌고 있어 거절했으면 false
     */
    public boolean run(String jobName) {
        if (!runningJobs.add(jobName)) {
            return false;
        }

        executor.execute(() -> execute(jobName, context -> {
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            return context.getBean(JobLauncher.class)
                    .run(context.getBean(jobName, Job.class), parameters);
        }));

        return true;
    }

    /**
     * 실패 지점부터 재시작. 이전 실행의 JobParameters 를 그대로 되살려 같은 JobInstance 를 이어 돌린다.
     * 성공한 Step 은 건너뛴다.
     *
     * @return 접수했으면 true, 이미 돌고 있어 거절했으면 false
     */
    public boolean restart(String jobName, long jobExecutionId) {
        if (!runningJobs.add(jobName)) {
            return false;
        }

        executor.execute(() -> execute(jobName, context -> {
            context.getBean(JobRegistry.class)
                    .register(new ReferenceJobFactory(context.getBean(jobName, Job.class)));

            long restartedId = context.getBean(JobOperator.class).restart(jobExecutionId);
            log.info("배치 재시작 접수: job={} 기준실행={} 새실행={}", jobName, jobExecutionId, restartedId);

            return null;
        }));

        return true;
    }

    /**
     * 백그라운드 스레드라 예외를 돌려줄 곳이 없다. 잡 안에서 난 실패는 리스너가 알림으로 남기지만,
     * 컨텍스트 기동 실패처럼 잡 바깥에서 터진 것은 여기서 로그로만 남는다.
     */
    private void execute(String jobName, BatchAction action) {
        try (AnnotationConfigApplicationContext context = createContext()) {
            JobExecution execution = action.run(context);

            if (execution != null) {
                log.info("배치 실행 종료: job={} 실행ID={} 상태={}",
                        jobName, execution.getId(), execution.getStatus());
            }
        } catch (Exception exception) {
            log.error("배치 실행에 실패했습니다: job={}", jobName, exception);
        } finally {
            runningJobs.remove(jobName);
        }
    }

    /**
     * batch.properties 의 산출물 경로는 cron 실행 위치를 기준으로 한 상대경로다. 톰캣 안에서 그대로
     * 쓰면 톰캣의 작업 디렉터리에 파일이 떨어지므로, 절대경로로 덮어쓴다.
     * addFirst 라 @PropertySource 로 읽은 batch.properties 보다 우선한다.
     */
    private AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        Map<String, Object> overrides = Map.of(
                "law.json.dir", workDir.resolve("law-json").toString(),
                "law.jsonl.path", workDir.resolve("law-jsonl").resolve("law_article.jsonl").toString()
        );

        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("admin-batch-override", overrides));

        try {
            context.register(Class.forName(BATCH_CONFIG_CLASS));
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("배치 실행 구성을 찾을 수 없습니다.", exception);
        }
        context.refresh();

        return context;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface BatchAction {
        JobExecution run(AnnotationConfigApplicationContext context) throws Exception;
    }
}

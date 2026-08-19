package com.example.project.batch.product.etf.tasklet;

import com.example.project.batch.product.etf.domain.EtfMarketDataRefreshResult;
import com.example.project.batch.product.etf.service.EtfMarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Log4j2
public class EtfMarketDataTasklet implements Tasklet {

    private final EtfMarketDataService marketDataService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Object parameter = chunkContext.getStepContext()
                .getJobParameters()
                .get("asOfDate");
        LocalDate asOfDate = parameter == null
                ? LocalDate.now()
                : LocalDate.parse(parameter.toString());

        EtfMarketDataRefreshResult result = marketDataService.refresh(asOfDate);
        log.info("ETF 배치 결과: 대상={}건, 저장 종가={}건, 상품데이터버전={}",
                result.getTargetCount(),
                result.getStoredPriceCount(),
                result.getPublishedDataVersionId());
        return RepeatStatus.FINISHED;
    }
}

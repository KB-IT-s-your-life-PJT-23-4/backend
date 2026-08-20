import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.AfterThread
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Test
import org.junit.runner.RunWith
import org.ngrinder.http.HTTPRequest
import org.ngrinder.http.HTTPRequestControl
import org.ngrinder.http.HTTPResponse

import java.util.concurrent.atomic.AtomicInteger

@RunWith(GrinderRunner)
class LoginConsultLoadTest {

    private static final String TARGET_HOST = "http://localhost:8080"
    private static final String USER_FILE = "./resources/test-users.csv"
    private static final int MAX_ERROR_LOGS_PER_PROCESS = 20
    private static final int MAX_ERROR_BODY_LENGTH = 1_000
    private static final String QUESTION = "개별 세액 계산이 아닌 일반적인 제도 설명을 요청합니다. 국내 거주자인 부모가 국내 거주자인 성년 자녀에게 증여할 때 적용되는 증여재산공제 한도와 최근 10년 합산 원칙을 추가 확인 없이 설명해주세요."

    private static GTest loginTest
    private static GTest consultTest
    private static List<Map<String, Object>> users
    private static final AtomicInteger consultErrorLogCount = new AtomicInteger()

    private final ThreadLocal<HTTPRequest> loginRequestHolder = new ThreadLocal<>()
    private final ThreadLocal<HTTPRequest> consultRequestHolder = new ThreadLocal<>()
    private final ThreadLocal<Map<String, Object>> userHolder = new ThreadLocal<>()

    @BeforeProcess
    static void beforeProcess() {
        HTTPRequestControl.setConnectionTimeout(25_000)
        HTTPRequestControl.setSocketTimeout(60_000)

        // HTTP 플러그인의 통계 등록은 워커 스레드가 시작되기 전에 완료되어야 한다.
        new HTTPRequest()

        loginTest = new GTest(1, "POST /api/auth/login")
        consultTest = new GTest(2, "POST /api/ai/consult")
        users = loadUsers(false)
    }

    @BeforeThread
    void beforeThread() {
        int agentNumber = grinder.agentNumber
        int processNumber = grinder.processNumber
        int threadNumber = grinder.threadNumber
        int threadsPerProcess = grinder.properties.getInt("grinder.threads", 1)
        int processesPerAgent = grinder.properties.getInt("grinder.processes", 1)
        int userIndex = (
                agentNumber * processesPerAgent * threadsPerProcess
                        + processNumber * threadsPerProcess
                        + threadNumber
        )

        if (userIndex >= users.size()) {
            throw new IllegalStateException(
                    "가상 사용자 수보다 테스트 계정 수가 적습니다. "
                            + "required=${userIndex + 1}, actual=${users.size()}"
            )
        }
        Map<String, Object> assignedUser = users.get(userIndex)

        loginRequestHolder.set(new HTTPRequest())
        consultRequestHolder.set(new HTTPRequest())
        userHolder.set(assignedUser)

        grinder.logger.info(
                "사용자 할당 agent={}, process={}, thread={}, index={}, email={}",
                agentNumber,
                processNumber,
                threadNumber,
                userIndex,
                assignedUser.email
        )

        // 한 시나리오 안에서 실행 순서를 유지하면서 API별 응답 시간을 분리한다.
        loginTest.record(this, "login")
        consultTest.record(this, "consult")
        grinder.statistics.delayReports = true
    }

    @AfterThread
    void afterThread() {
        loginRequestHolder.remove()
        consultRequestHolder.remove()
        userHolder.remove()
    }

    @Test
    void scenario() {
        String accessToken = login()
        consult(accessToken)
    }

    String login() {
        HTTPRequest loginRequest = loginRequestHolder.get()
        Map<String, Object> currentUser = userHolder.get()

        loginRequest.setHeaders(jsonHeaders())
        String requestBody = JsonOutput.toJson([
                email   : currentUser.email,
                password: currentUser.password
        ])

        HTTPResponse response = loginRequest.POST(
                "${TARGET_HOST}/api/auth/login",
                requestBody.getBytes("UTF-8")
        )
        assertHttpStatus(response, 200, "로그인")

        Map<String, Object> body = parseBody(response)
        String accessToken = body.data?.accessToken as String
        assertNotNull("로그인 응답에 accessToken이 없습니다.", accessToken)
        return accessToken
    }

    void consult(String accessToken) {
        HTTPRequest consultRequest = consultRequestHolder.get()

        consultRequest.setHeaders(authHeaders(accessToken))
        String requestBody = JsonOutput.toJson([question: QUESTION])

        HTTPResponse response = consultRequest.POST(
                "${TARGET_HOST}/api/ai/consult",
                requestBody.getBytes("UTF-8")
        )

        logConsultHttpError(response)
        assertHttpStatus(response, 200, "AI 상담")

        Map<String, Object> body = parseBody(response)
        if (body.data == null) {
            logConsultPayloadError(response)
        }
        assertNotNull("AI 상담 응답의 data가 없습니다.", body.data)
    }

    private static void logConsultHttpError(HTTPResponse response) {
        if (response.statusCode == 200 || !acquireConsultErrorLogSlot()) {
            return
        }

        grinder.logger.error(
                "AI 상담 실패 status={}, body={}",
                response.statusCode,
                safeResponseBody(response)
        )
    }

    private static void logConsultPayloadError(HTTPResponse response) {
        if (!acquireConsultErrorLogSlot()) {
            return
        }

        grinder.logger.error(
                "AI 상담 응답에 data가 없습니다. status={}, body={}",
                response.statusCode,
                safeResponseBody(response)
        )
    }

    private static boolean acquireConsultErrorLogSlot() {
        return consultErrorLogCount.incrementAndGet() <= MAX_ERROR_LOGS_PER_PROCESS
    }

    private static String safeResponseBody(HTTPResponse response) {
        String responseBody = response.getBodyText()
        if (responseBody == null || responseBody.isEmpty()) {
            return "[EMPTY]"
        }

        return responseBody.length() <= MAX_ERROR_BODY_LENGTH
                ? responseBody
                : responseBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...[TRUNCATED]"
    }

    private static Map<String, String> jsonHeaders() {
        return [
                "Content-Type": "application/json; charset=UTF-8",
                "Accept"      : "application/json"
        ]
    }

    private static Map<String, String> authHeaders(String accessToken) {
        Map<String, String> headers = jsonHeaders()
        // nGrinder 헤더 변환기는 Groovy GString을 java.lang.String으로 캐스팅하지 못한다.
        headers.put("Authorization", "Bearer ${accessToken}".toString())
        return headers
    }

    private static Map<String, Object> parseBody(HTTPResponse response) {
        return new JsonSlurper().parseText(response.getBodyText()) as Map<String, Object>
    }

    private static void assertHttpStatus(
            HTTPResponse response,
            int expectedStatus,
            String operation
    ) {
        assertEquals(
                "${operation} HTTP 상태가 올바르지 않습니다.",
                expectedStatus,
                response.statusCode
        )
    }

    private static List<Map<String, Object>> loadUsers(boolean familyIdRequired) {
        File file = new File(USER_FILE)
        if (!file.isFile()) {
            throw new IllegalStateException("${USER_FILE} 파일이 없습니다.")
        }

        List<String> lines = file.readLines("UTF-8")
                .findAll { String line -> line?.trim() && !line.trim().startsWith("#") }
        if (!lines.isEmpty() && lines.first().toLowerCase().startsWith("email,")) {
            lines = lines.drop(1)
        }

        List<Map<String, Object>> loadedUsers = lines.collect { String line ->
            String[] columns = line.split(",", -1)
            int requiredColumnCount = familyIdRequired ? 3 : 2
            if (columns.length < requiredColumnCount) {
                throw new IllegalStateException("테스트 사용자 CSV 형식이 올바르지 않습니다.")
            }
            return [
                    email   : columns[0].trim(),
                    password: columns[1],
                    familyId: columns.length >= 3 && columns[2].trim()
                            ? columns[2].trim().toLong()
                            : null
            ]
        }

        if (loadedUsers.isEmpty()) {
            throw new IllegalStateException("부하 테스트 사용자가 한 명도 없습니다.")
        }
        return loadedUsers
    }

}

import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Test
import org.junit.runner.RunWith
import org.ngrinder.http.HTTPRequest
import org.ngrinder.http.HTTPRequestControl
import org.ngrinder.http.HTTPResponse

@RunWith(GrinderRunner)
class LoginSimulationLoadTest {

    private static final String TARGET_HOST = "http://localhost:8080"
    private static final String USER_FILE = "./resources/test-users.csv"
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul")
    private static final String TEST_RUN_ID = UUID.randomUUID().toString()
    private static final String GIFT_DATE =
            LocalDate.now(SERVICE_ZONE).plusDays(1).toString()
    private static final List<Long> REQUEST_AMOUNTS = [
            30_000_000L,
            50_000_000L,
            100_000_000L
    ].asImmutable()

    private static GTest loginTest
    private static GTest executeTest
    private static List<Map<String, Object>> users

    private HTTPRequest loginRequest
    private HTTPRequest executeRequest

    private Map<String, Object> user
    private int requestAmountIndex

    @BeforeProcess
    static void beforeProcess() {
        // HTTP 플러그인의 통계 등록은 워커 스레드가 시작되기 전에 완료되어야 한다.
        new HTTPRequest()

        HTTPRequestControl.setConnectionTimeout(40_000)

        loginTest = new GTest(1, "POST /api/auth/login")
        executeTest = new GTest(2, "POST /api/gs")
        users = loadUsers(true)
    }

    @BeforeThread
    void beforeThread() {
        loginRequest = new HTTPRequest()
        executeRequest = new HTTPRequest()
        user = users.get(resolveUserIndex(users.size()))
        requestAmountIndex = 0

        loginTest.record(this, "login")
        executeTest.record(this, "executeSimulation")
        grinder.statistics.delayReports = true
    }

    @Test
    void scenario() {
        String accessToken = login()
        long requestedAmount = nextRequestedAmount()
        executeSimulation(accessToken, requestedAmount)
    }

    String login() {
        loginRequest.setHeaders(jsonHeaders())
        String requestBody = JsonOutput.toJson([
                email   : user.email,
                password: user.password
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

    void executeSimulation(String accessToken, long requestedAmount) {
        Map<String, String> headers = authHeaders(accessToken)
        headers.put(
                "Idempotency-Key",
                idempotencyKey(user.familyId as Long, requestedAmount)
        )
        executeRequest.setHeaders(headers)

        String requestBody = JsonOutput.toJson([
                familyId             : user.familyId,
                requestedAmount      : requestedAmount,
                taxPaymentMethod     : "RECIPIENT_PAYS",
                investmentPeriodMonths: 120,
                giftDate             : GIFT_DATE
        ])

        HTTPResponse response = executeRequest.POST(
                "${TARGET_HOST}/api/gs",
                requestBody.getBytes("UTF-8")
        )
        assertHttpStatus(response, 201, "시뮬레이션 실행")

        Map<String, Object> body = parseBody(response)
        Map<String, Object> simulation = body.data as Map<String, Object>
        assertNotNull("시뮬레이션 응답의 data가 없습니다.", simulation)
        assertNotNull("시뮬레이션 응답의 simulationId가 없습니다.", simulation.simulationId)
    }

    private long nextRequestedAmount() {
        long requestedAmount = REQUEST_AMOUNTS.get(requestAmountIndex)
        requestAmountIndex = (requestAmountIndex + 1) % REQUEST_AMOUNTS.size()
        return requestedAmount
    }

    private static String idempotencyKey(
            Long familyId,
            long requestedAmount
    ) {
        String source = "${TEST_RUN_ID}:${familyId}:${requestedAmount}".toString()
        return UUID.nameUUIDFromBytes(source.getBytes("UTF-8")).toString()
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

    private static int resolveUserIndex(int userCount) {
        int threadsPerProcess = grinder.properties.getInt("grinder.threads", 1)
        int processesPerAgent = grinder.properties.getInt("grinder.processes", 1)
        int index = grinder.agentNumber * processesPerAgent * threadsPerProcess
                + grinder.processNumber * threadsPerProcess
                + grinder.threadNumber

        if (index >= userCount) {
            throw new IllegalStateException(
                    "가상 사용자 수보다 테스트 계정 수가 적습니다. required=${index + 1}, actual=${userCount}"
            )
        }
        return index
    }
}

const { spawnSync } = require("node:child_process");

const isWindows = process.platform === "win32";
const gradleWrapper = isWindows ? "gradlew.bat" : "./gradlew";

const result = spawnSync(
    gradleWrapper,
    ["test", ...process.argv.slice(2)],
    {
        stdio: "inherit",
        shell: isWindows,
    }
);

if (result.error) {
    console.error(`Gradle 실행 실패: ${result.error.message}`);
    process.exit(1);
}

process.exit(result.status ?? 1);
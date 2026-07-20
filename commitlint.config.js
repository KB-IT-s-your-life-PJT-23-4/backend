module.exports = {
    extends: ['@commitlint/config-conventional'],

    rules: {
        'subject-empty': [0],
        'type-empty': [0],
        'header-custom-pattern': [2, 'always'],
    },

    plugins: [
        {
            rules: {
                'header-custom-pattern': ({ header }) => {
                    const pattern =
                        /^KAN-\d+\s(feat|fix|docs|style|refactor|perf|test|chore|build):\s.+/;

                    const isValid = pattern.test(header);

                    return [
                        isValid,
                        isValid ? '' : 'The header must follow the pattern: "KAN-{number} type: message"',
                    ];
                },
            },
        },
    ],
};
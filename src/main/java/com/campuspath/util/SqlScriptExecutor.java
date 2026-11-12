package com.campuspath.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class SqlScriptExecutor {
    private SqlScriptExecutor() {
    }

    static void execute(Connection connection, String script) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        for (String command : split(script)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(command);
            }
        }
    }

    static List<String> split(String script) {
        Objects.requireNonNull(script, "script");
        ArrayList<String> commands = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        String dollarDelimiter = null;
        int blockCommentDepth = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;

        for (int index = 0; index < script.length(); index++) {
            char current = script.charAt(index);
            char next = index + 1 < script.length() ? script.charAt(index + 1) : '\0';

            if (lineComment) {
                command.append(current);
                if (current == '\n') lineComment = false;
                continue;
            }
            if (blockCommentDepth > 0) {
                command.append(current);
                if (current == '/' && next == '*') {
                    command.append(next);
                    index++;
                    blockCommentDepth++;
                } else if (current == '*' && next == '/') {
                    command.append(next);
                    index++;
                    blockCommentDepth--;
                }
                continue;
            }
            if (dollarDelimiter != null) {
                if (script.startsWith(dollarDelimiter, index)) {
                    command.append(dollarDelimiter);
                    index += dollarDelimiter.length() - 1;
                    dollarDelimiter = null;
                } else {
                    command.append(current);
                }
                continue;
            }
            if (singleQuoted) {
                command.append(current);
                if (current == '\'' && next == '\'') {
                    command.append(next);
                    index++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                command.append(current);
                if (current == '"' && next == '"') {
                    command.append(next);
                    index++;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (current == '-' && next == '-') {
                command.append(current).append(next);
                index++;
                lineComment = true;
            } else if (current == '/' && next == '*') {
                command.append(current).append(next);
                index++;
                blockCommentDepth = 1;
            } else if (current == '\'') {
                command.append(current);
                singleQuoted = true;
            } else if (current == '"') {
                command.append(current);
                doubleQuoted = true;
            } else if (current == '$') {
                String delimiter = readDollarDelimiter(script, index);
                if (delimiter == null) {
                    command.append(current);
                } else {
                    command.append(delimiter);
                    index += delimiter.length() - 1;
                    dollarDelimiter = delimiter;
                }
            } else if (current == ';') {
                addCommand(commands, command);
            } else {
                command.append(current);
            }
        }

        if (singleQuoted || doubleQuoted || dollarDelimiter != null || blockCommentDepth > 0) {
            throw new IllegalArgumentException("The SQL script contains an unterminated token");
        }
        addCommand(commands, command);
        return List.copyOf(commands);
    }

    private static String readDollarDelimiter(String script, int start) {
        int closing = script.indexOf('$', start + 1);
        if (closing < 0) return null;
        String tag = script.substring(start + 1, closing);
        if (tag.isEmpty() || tag.matches("[A-Za-z_][A-Za-z_0-9]*")) {
            return script.substring(start, closing + 1);
        }
        return null;
    }

    private static void addCommand(List<String> commands, StringBuilder command) {
        String value = command.toString().trim();
        if (!value.isEmpty()) {
            commands.add(value);
        }
        command.setLength(0);
    }
}

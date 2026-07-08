package phucuong.ht.modules.training;

import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class TrainingScoreFormulaEvaluator {

    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final CommonValidationHelper commonValidationHelper;

    public TrainingScoreFormulaEvaluator(CommonValidationHelper commonValidationHelper) {
        this.commonValidationHelper = commonValidationHelper;
    }

    public BigDecimal evaluate(String expression, Map<String, BigDecimal> variables) {
        if (expression == null || expression.isBlank()) {
            throw commonValidationHelper.validationError("Score formula expression is required");
        }
        Parser parser = new Parser(expression, variables);
        BigDecimal result = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw commonValidationHelper.validationError("Invalid score formula expression");
        }
        return result.stripTrailingZeros();
    }

    public Set<String> extractVariables(String expression) {
        if (expression == null || expression.isBlank()) {
            return Set.of();
        }
        Set<String> variables = new HashSet<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < expression.length(); index++) {
            char ch = expression.charAt(index);
            if (Character.isLetter(ch) || Character.isDigit(ch) || ch == '_') {
                current.append(ch);
                continue;
            }
            flushVariable(current, variables);
        }
        flushVariable(current, variables);
        return variables;
    }

    private void flushVariable(StringBuilder current, Set<String> variables) {
        if (current.isEmpty()) {
            return;
        }
        String value = current.toString();
        if (!Character.isDigit(value.charAt(0))) {
            variables.add(value);
        }
        current.setLength(0);
    }

    private final class Parser {
        private final String expression;
        private final Map<String, BigDecimal> variables;
        private int position = 0;

        private Parser(String expression, Map<String, BigDecimal> variables) {
            this.expression = expression;
            this.variables = variables;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                    continue;
                }
                if (match('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                    continue;
                }
                return value;
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value = value.multiply(parseFactor(), MATH_CONTEXT);
                    continue;
                }
                if (match('/')) {
                    BigDecimal divisor = parseFactor();
                    if (BigDecimal.ZERO.compareTo(divisor) == 0) {
                        throw commonValidationHelper.validationError("Score formula cannot divide by zero");
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                    continue;
                }
                return value;
            }
        }

        private BigDecimal parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return parseFactor().negate(MATH_CONTEXT);
            }
            if (match('(')) {
                BigDecimal nested = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw commonValidationHelper.validationError("Unclosed parentheses in score formula");
                }
                return nested;
            }
            if (peekLetter()) {
                String variable = readVariable();
                BigDecimal value = variables.get(variable);
                if (value == null) {
                    throw commonValidationHelper.validationError("Unknown score component in formula: " + variable);
                }
                return value;
            }
            return readNumber();
        }

        private BigDecimal readNumber() {
            skipWhitespace();
            int start = position;
            boolean decimalFound = false;
            while (!isEnd()) {
                char ch = expression.charAt(position);
                if (Character.isDigit(ch)) {
                    position++;
                    continue;
                }
                if (ch == '.' && !decimalFound) {
                    decimalFound = true;
                    position++;
                    continue;
                }
                break;
            }
            if (start == position) {
                throw commonValidationHelper.validationError("Invalid score formula expression");
            }
            return new BigDecimal(expression.substring(start, position), MATH_CONTEXT);
        }

        private String readVariable() {
            int start = position;
            while (!isEnd()) {
                char ch = expression.charAt(position);
                if (Character.isLetter(ch) || Character.isDigit(ch) || ch == '_') {
                    position++;
                    continue;
                }
                break;
            }
            return expression.substring(start, position);
        }

        private boolean peekLetter() {
            return !isEnd() && Character.isLetter(expression.charAt(position));
        }

        private boolean match(char expected) {
            if (isEnd() || expression.charAt(position) != expected) {
                return false;
            }
            position++;
            return true;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }

        private boolean isEnd() {
            return position >= expression.length();
        }
    }
}

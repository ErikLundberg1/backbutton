package se.kth.youeye;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class InputAnalyzer {

    LinkedList<Expression> expressions;
    List<InputEvent> inputEvents;
    private final long keepAliveDuration; // The time in ms that we will store old expressions
    private final static String TAG = "InputAnalyzer";

    /**
     * The input event class serves as a definition for how an input event is raised
     * based on the history of recorded facial expressions
     */
    private static class InputEvent implements Comparable<InputEvent> {
        // The expression that signifies the end of the input action, e.g. opening the eyes for a blink
        private final int endExpression;
        // The expression that must be held for a set amount of time during the input action,
        // for example keeping the eyes closed during a blink
        private final int activeExpression;
        // The duration in milliseconds that the active expression must be held
        private final int durationInMs;
        // The action to be executed, e.g. clicking a selected UI object
        private final int inputAction;

        private InputEvent(@Expression.ExpressionTypeDef int endExpression,
                           @Expression.ExpressionTypeDef int activeExpression,
                           int durationInMs,
                           @UINavigator.ActionTypeDef int inputAction) {
            this.endExpression = endExpression;
            this.activeExpression = activeExpression;
            this.durationInMs = durationInMs;
            this.inputAction = inputAction;
        }

        /**
         * We want to be able to sort our input events so that we can make sure the correct one
         * is activated. This allows us to have events for blinks of different lengths, for example
         * 0.1 and 0.5 seconds. The one with the longest duration will be checked against the expression
         * history first and if that does not match, the one with the shorter duration will be checked.
         */
        @Override
        public int compareTo(InputEvent other) {
            // If the InputEvents have different end/active expressions we simply sort them
            // based on this, the ordering in this case should not really matter
            if (this.endExpression != other.endExpression)
                return this.endExpression - other.endExpression;
            if (this.activeExpression != other.activeExpression)
                return this.activeExpression - other.activeExpression;
            // If the events are both looking for the same active and end expression,
            // we want the one with the longest duration to be ordered first
            if (this.durationInMs >= other.durationInMs)
                return other.durationInMs - this.durationInMs;
            else
                return this.durationInMs - other.durationInMs;
        }

        // TODO: make this method better or remove it, kind of crummy for debug purposes only
        @Override
        public String toString() {
            return String.format(Locale.ENGLISH,"InputAction: {end: %d, act: %d, ms: %d, ui: %d}",
                                  endExpression, activeExpression, durationInMs, inputAction);
        }
    }

    public InputAnalyzer(long keepAliveDuration) {
        expressions = new LinkedList<>();
        inputEvents = new ArrayList<>();
        this.keepAliveDuration = keepAliveDuration;

        createInputEvents();
    }

    /**
     * Analyze a facial expression and see if it matches any of the set input events based
     * on the stored history of expressions
     * @param expression the expression to analyze
     * @return the magic constant value for an UI action to perform
     */
    public @UINavigator.ActionTypeDef int analyze(Expression expression) {
        removeOldExpressions();

        long currentTime = System.currentTimeMillis();

        for (InputEvent event : inputEvents) {
            if (expression.has(event.endExpression)) {
                Expression lastMatchingExpression = expression;
                for (Expression expr : expressions) {
                    if (!expr.has(event.activeExpression))
                        break;
                    lastMatchingExpression = expr;
                }
                // TODO: We currently only return the fist input event that matches,
                // this might not be a smart solution, and requires us to order our InputEvents
                // in the correct order for the right one to fire.
                if (currentTime - lastMatchingExpression.timestamp >= event.durationInMs) {
                    expressions.addFirst(expression);
                    Log.d(TAG, "Matched " + event);
                    return event.inputAction;
                }
            }
        }

        expressions.addFirst(expression);
        return UINavigator.NO_ACTION;
    }

    /**
     * Clean up any expressions older than our keep alive limit
     */
    private void removeOldExpressions() {
        long currentTime = System.currentTimeMillis();
        Iterator<Expression> iter = expressions.descendingIterator();
        while (iter.hasNext()) {
            Expression exp = iter.next();
            // Break once we encounter an expression that should remain alive
            if (currentTime - exp.timestamp < keepAliveDuration)
                break;
            iter.remove();
        }
    }

    /**
     * Set up our list of input events, at the moment we simply hard code two different
     * blinks. In the future, the user should be able to define these input events themselves
     * and we should load them from settings.
     */
    private void createInputEvents() {
        // TODO: Remove these hardcoded InputEvents and load them from a settings file instead
        inputEvents.add(new InputEvent(Expression.EYES_OPEN, Expression.EYES_CLOSED,
                200, UINavigator.SELECT_NEXT));
        inputEvents.add(new InputEvent(Expression.EYES_OPEN, Expression.EYES_CLOSED,
                800, UINavigator.CLICK));
        // We sort the events so that they will be evaluated in the correct order
        Collections.sort(inputEvents);
        Log.d(TAG, "Created and sorted inputEvents: " + inputEvents);
    }
}

package hu.mudlee.core.ui;

/** Evaluates a numeric stat value and returns the appropriate {@link WarningLevel}. */
@FunctionalInterface
public interface StatThreshold {
    WarningLevel evaluate(float value);
}

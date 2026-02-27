package hu.mudlee.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A stack-based screen manager.
 *
 * <p>Add it to {@link Game#components} once, then use {@link #set}, {@link #push}, and
 * {@link #pop} to navigate between screens:
 *
 * <ul>
 *   <li>{@link #set} — replaces the entire stack (new game, level change).
 *   <li>{@link #push} — overlays a screen on top without disposing the one below (pause menu).
 *   <li>{@link #pop} — removes the top screen and resumes the one underneath.
 * </ul>
 *
 * <p>Only the top screen receives {@link #update} and {@link #draw} calls. {@link #resize} is
 * forwarded to every screen in the stack.
 */
public final class ScreenManager extends GameService {

    private final Deque<Screen> stack = new ArrayDeque<>();

    /** Replaces the entire stack with {@code screen}. All previous screens are disposed. */
    public void set(Screen screen) {
        while (!stack.isEmpty()) {
            var top = stack.pop();
            top.hide();
            top.dispose();
        }
        stack.push(screen);
        screen.show();
    }

    /**
     * Pushes {@code screen} on top of the stack. The previous top screen is hidden but not
     * disposed.
     */
    public void push(Screen screen) {
        if (!stack.isEmpty()) {
            stack.peek().hide();
        }
        stack.push(screen);
        screen.show();
    }

    /** Pops and disposes the top screen. The screen underneath is shown again. */
    public void pop() {
        if (stack.isEmpty()) {
            return;
        }
        var top = stack.pop();
        top.hide();
        top.dispose();
        if (!stack.isEmpty()) {
            stack.peek().show();
        }
    }

    @Override
    public void update(GameTime gameTime) {
        if (!stack.isEmpty()) {
            stack.peek().update(gameTime);
        }
    }

    @Override
    public void draw(GameTime gameTime) {
        if (!stack.isEmpty()) {
            stack.peek().draw(gameTime);
        }
    }

    @Override
    public void resize(int width, int height) {
        for (var screen : stack) {
            screen.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        while (!stack.isEmpty()) {
            var top = stack.pop();
            top.hide();
            top.dispose();
        }
    }
}

package hu.mudlee.core;

import hu.mudlee.core.window.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

/**
 * A stack-based screen manager.
 *
 * <p>Add it via {@link Game#addModule} once, then use {@link #set}, {@link #push}, and
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
 *
 * <p>Transitions requested during {@code update()} or {@code draw()} are deferred and applied
 * at the end of the frame, so the currently executing screen is never mutated mid-callback.
 */
public final class ScreenManager extends GameModule {

    private static ScreenManager instance;

    private final Deque<Screen> stack = new ArrayDeque<>();
    private final Queue<Runnable> pendingTransitions = new ArrayDeque<>();
    private boolean processingFrame;

    public ScreenManager() {
        instance = this;
    }

    public static String getActiveScreenName() {
        if (instance == null || instance.stack.isEmpty()) {
            return "";
        }
        return instance.stack.peek().getClass().getSimpleName();
    }

    /** Replaces the entire stack with {@code screen}. All previous screens are disposed. */
    public void set(Screen screen) {
        if (processingFrame) {
            pendingTransitions.add(() -> applySet(screen));
        } else {
            applySet(screen);
        }
    }

    /**
     * Pushes {@code screen} on top of the stack. The previous top screen is hidden but not
     * disposed.
     */
    public void push(Screen screen) {
        if (processingFrame) {
            pendingTransitions.add(() -> applyPush(screen));
        } else {
            applyPush(screen);
        }
    }

    /** Pops and disposes the top screen. The screen underneath is resumed. */
    public void pop() {
        if (processingFrame) {
            pendingTransitions.add(this::applyPop);
        } else {
            applyPop();
        }
    }

    @Override
    public void update(GameTime gameTime) {
        processingFrame = true;
        if (!stack.isEmpty()) {
            stack.peek().update(gameTime);
        }
    }

    @Override
    public void draw(GameTime gameTime) {
        if (!stack.isEmpty()) {
            stack.peek().draw(gameTime);
        }
        processingFrame = false;
        drainPendingTransitions();
    }

    @Override
    public void resize(int width, int height) {
        for (var screen : stack) {
            screen.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        pendingTransitions.clear();
        while (!stack.isEmpty()) {
            var top = stack.pop();
            top.hide();
            top.dispose();
        }
    }

    private void applySet(Screen screen) {
        while (!stack.isEmpty()) {
            var top = stack.pop();
            top.hide();
            top.dispose();
        }
        stack.push(screen);
        screen.show();
        var size = Window.getSize();
        screen.resize(size.x, size.y);
    }

    private void applyPush(Screen screen) {
        if (!stack.isEmpty()) {
            stack.peek().hide();
        }
        stack.push(screen);
        screen.show();
        var size = Window.getSize();
        screen.resize(size.x, size.y);
    }

    private void applyPop() {
        if (stack.isEmpty()) {
            return;
        }
        var top = stack.pop();
        top.hide();
        top.dispose();
        if (!stack.isEmpty()) {
            stack.peek().resume();
        }
    }

    private void drainPendingTransitions() {
        while (!pendingTransitions.isEmpty()) {
            pendingTransitions.poll().run();
        }
    }
}

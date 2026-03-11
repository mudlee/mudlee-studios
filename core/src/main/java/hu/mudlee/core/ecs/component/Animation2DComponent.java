package hu.mudlee.core.ecs.component;

import hu.mudlee.core.ecs.Component;
import hu.mudlee.core.render.animation.Animation2D;
import hu.mudlee.core.render.animation.AnimationPlayer2D;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Animation2DComponent implements Component {

    public final AnimationPlayer2D player = new AnimationPlayer2D();
    public final Map<String, Animation2D> clips = new LinkedHashMap<>();

    public Animation2DComponent add(String name, Animation2D clip) {
        clips.put(name, clip);
        return this;
    }

    public void play(String name) {
        var clip = clips.get(name);
        if (clip != null) {
            player.play(clip);
        }
    }
}

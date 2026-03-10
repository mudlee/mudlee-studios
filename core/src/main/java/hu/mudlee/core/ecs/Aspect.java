package hu.mudlee.core.ecs;

public final class Aspect {

    final Class<? extends Component>[] all;

    @SafeVarargs
    private Aspect(Class<? extends Component>... all) {
        this.all = all;
    }

    @SafeVarargs
    public static Aspect all(Class<? extends Component>... types) {
        return new Aspect(types);
    }
}

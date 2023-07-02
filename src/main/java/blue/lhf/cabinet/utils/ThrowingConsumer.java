package blue.lhf.cabinet.utils;

@FunctionalInterface
public interface ThrowingConsumer<Input, T extends Throwable> {
    void accept(final Input input) throws T;
}

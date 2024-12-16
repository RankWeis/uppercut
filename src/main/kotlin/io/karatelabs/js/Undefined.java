package io.karatelabs.js;

public class Undefined {

    public static final Undefined INSTANCE = new Undefined();

    public static final Number NAN = Double.NaN;

    private Undefined() {
        // singleton
    }

    @Override
    public String toString() {
        return "undefined";
    }

}

package com.example.domain;

/**
 * A problem fact representing one possible value the planning variable can take.
 * Replace with your real value type (Room, Equipment, Timeslot, ...).
 */
public class MyValue {
    private String id;

    public MyValue() {}
    public MyValue(String id) { this.id = id; }

    public String getId() { return id; }

    @Override public String toString() { return id; }
}

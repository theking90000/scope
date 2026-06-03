package be.theking90000.di.core;

public record Edge<T>(Scope<T> parent, boolean owns, boolean visible) {}


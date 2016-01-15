package com.spothero.volley;

import com.fasterxml.jackson.databind.JavaType;

/**
 * Created by kdunn on 1/2/2016.
 */
public interface JacksonTypeProvider {

    /**
     * Called by the library to get the {@link com.fasterxml.jackson.databind.JavaType}
     * used to parse the network response. For simple POJOs, return a
     * {@link com.fasterxml.jackson.databind.type.SimpleType}. For lists and arrays,
     * return one of the values constructed using {@link com.fasterxml.jackson.databind.type.TypeFactory}
     *
     * @return The type that the network response should be parsed into.
     */
    JavaType getReturnType();
}

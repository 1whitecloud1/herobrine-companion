package com.whitecloud233.modid.herobrine_companion.structure.template;

/** Small server-confirmed catalog record. Block data never enters the LLM context. */
public record TemplateEntry(String id, String name, String format, int x, int y, int z, int blocks) { }

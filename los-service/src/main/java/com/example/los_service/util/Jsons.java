package com.example.los_service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
public class Jsons {
    public static final ObjectMapper M = new ObjectMapper();
    public static String toJson(Object o){ try { return M.writeValueAsString(o);} catch(Exception e){ throw new RuntimeException(e);} }
}

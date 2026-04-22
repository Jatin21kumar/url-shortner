package com.jatin.url_shortner.util;

public class Base62Util {
    
    static final String base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String toBase62(long num){
        if (num == 0) {
           return "0";
        }
        
        StringBuilder sb = new StringBuilder();

        while(num > 0){
            long rem = num % 62;
            sb.append(base62Chars.charAt((int)rem));
            num = num / 62;
        }

        return sb.reverse().toString();
    }
}

package cc.coopersoft.keycloak.phone.utils;

import org.apache.commons.lang.StringUtils;

import java.util.regex.Pattern;

public class RegexUtils {
    /**
     * 转义正则特殊字符 （$()*+.[]?\^{},|）
     *
     * @param keyword
     * @return
     */
    public static String escapeExprSpecialWord(String keyword) {
        if (StringUtils.isNotBlank(keyword)) {
            String[] fbsArr = { "\\", "$", "(", ")", "+", ".", "[", "]", "?", "^", "{", "}", "|" };
            for (String key : fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\" + key);
                }
            }
        }
        return keyword;
    }

    public static String buildExprFromGlob(String glob){
        glob = escapeExprSpecialWord(glob).replace("\\*", ".*?");
        glob = "^" + glob + "$";
        return glob;
    }

    public static boolean matchGlob(String str, String match){
        String regex = buildExprFromGlob(str);
        return Pattern.matches(regex, str);
    }
}

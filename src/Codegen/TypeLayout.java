package src.Codegen;

import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.HashSet;
import java.util.Set;

final class TypeLayout {

    private static final int POINTER_ALIGNMENT = 8;

    private TypeLayout() {
    }

    static int alignmentOf(final TypeRef type) {
        return alignmentOf(type, new HashSet<>());
    }

    private static int alignmentOf(final TypeRef type, final Set<String> visiting) {
        switch (type) {
            case null -> {
                return 0;
            }
            case ZType builtin -> {
                return switch (builtin) {
                    case BOOL -> 1;
                    case INT32, UINT32, FLT32 -> 4;
                    case INT64, UINT64, FLT64 -> 8;
                    case PTR, INT32_PTR, INT64_PTR, UINT32_PTR, UINT64_PTR,
                         FLT32_PTR, FLT64_PTR, BOOL_PTR, STR_PTR, STRING -> POINTER_ALIGNMENT;
                    case VOID, VARARG -> 0;
                };
            }
            case StructType struct -> {
                if (!visiting.add(struct.zName())) {
                    return 0;
                }
                try {
                    int alignment = 1;
                    for (final StructType.Field field : struct.fields()) {
                        alignment = Math.max(alignment, alignmentOf(field.type(), visiting));
                    }
                    return alignment;
                } finally {
                    visiting.remove(struct.zName());
                }
            }
            default -> {
            }
        }
        return 0;
    }

    static int alignmentOf(final String llvmType) {
        return alignmentOf(llvmType, new HashSet<>());
    }

    private static int alignmentOf(final String llvmType, final Set<String> visiting) {
        if (llvmType == null) {
            return 0;
        }
        final String type = llvmType.trim();
        if (type.isEmpty()) {
            return 0;
        }
        if (type.endsWith("*")) {
            return POINTER_ALIGNMENT;
        }
        if (type.startsWith("%")) {
            final StructType struct = StructRegistry.lookup(type.substring(1));
            return struct == null ? 0 : alignmentOf(struct, visiting);
        }
        if (type.startsWith("<{")) {
            return 1;
        }
        if (type.startsWith("{")) {
            return aggregateAlignment(type, visiting);
        }
        if (type.startsWith("[")) {
            final int separator = topLevelX(type);
            if (separator < 0) {
                return 0;
            }
            return alignmentOf(type.substring(separator + 1, type.length() - 1), visiting);
        }
        if (type.startsWith("<")) {
            return 0;
        }
        if (type.startsWith("i") && type.length() > 1 && Character.isDigit(type.charAt(1))) {
            return integerAlignment(type);
        }
        return switch (type) {
            case "half" -> 2;
            case "float" -> 4;
            case "double" -> 8;
            case "fp128", "x86_fp80", "ppc_fp128" -> 16;
            case "ptr" -> POINTER_ALIGNMENT;
            default -> 0;
        };
    }

    private static int aggregateAlignment(final String type, final Set<String> visiting) {
        final String body = type.substring(1, type.length() - 1);
        int alignment = 1;
        int start = 0;
        int depth = 0;
        for (int i = 0; i <= body.length(); i++) {
            final boolean end = i == body.length();
            if (!end) {
                final char c = body.charAt(i);
                if (c == '[' || c == '{' || c == '<' || c == '(') {
                    depth++;
                } else if (c == ']' || c == '}' || c == '>' || c == ')') {
                    depth--;
                }
            }
            if (end || (body.charAt(i) == ',' && depth == 0)) {
                final String field = body.substring(start, i).trim();
                if (!field.isEmpty()) {
                    alignment = Math.max(alignment, alignmentOf(field, visiting));
                }
                start = i + 1;
            }
        }
        return alignment;
    }

    private static int topLevelX(final String type) {
        int depth = 0;
        for (int i = 1; i < type.length() - 1; i++) {
            final char c = type.charAt(i);
            if (c == '[' || c == '{' || c == '<' || c == '(') {
                depth++;
            } else if (c == ']' || c == '}' || c == '>' || c == ')') {
                depth--;
            } else if (c == 'x' && depth == 0
                    && Character.isWhitespace(type.charAt(i - 1))
                    && Character.isWhitespace(type.charAt(i + 1))) {
                return i;
            }
        }
        return -1;
    }

    private static int integerAlignment(final String type) {
        try {
            final int bits = Integer.parseInt(type.substring(1));
            final int bytes = (bits + 7) / 8;
            int alignment = 1;
            while (alignment < bytes) {
                alignment <<= 1;
            }
            return alignment;
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}

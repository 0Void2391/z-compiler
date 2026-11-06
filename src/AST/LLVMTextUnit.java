package src.AST;

import src.Parser.TypeRef;

public record LLVMTextUnit(String text, TypeRef type) {

    /** @return true when this is a register rather than a literal */
    public boolean isRegister() {
        return text.startsWith("%");
    }
}
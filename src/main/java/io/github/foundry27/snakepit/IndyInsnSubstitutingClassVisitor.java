package io.github.foundry27.snakepit;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author Foundry
 */
public final class IndyInsnSubstitutingClassVisitor extends ClassVisitor {

    private final String className;

    public IndyInsnSubstitutingClassVisitor(final ClassVisitor cv, final String className) {
        super(Opcodes.ASM5, cv);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new IndyInsnSubstitutingMethodVisitor(cv.visitMethod(access, name, descriptor, signature, exceptions), cv, className, name);
    }
}

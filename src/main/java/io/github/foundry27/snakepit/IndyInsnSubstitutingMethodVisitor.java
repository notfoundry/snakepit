package io.github.foundry27.snakepit;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Foundry
 */
public final class IndyInsnSubstitutingMethodVisitor extends MethodVisitor {

    private final ClassVisitor cv;

    private final String className;

    private final String callerMethodName;

    private long generationMethodID;

    public IndyInsnSubstitutingMethodVisitor(final MethodVisitor mv, final ClassVisitor cv, final String className, final String callerMethodName) {
        super(Opcodes.ASM5, mv);
        this.cv = cv;
        this.className = className;
        switch (callerMethodName) {
            case "<init>":
                this.callerMethodName = "new";
                break;
            case "<clinit>":
                this.callerMethodName = "static";
                break;
            default:
                this.callerMethodName = callerMethodName;
                break;
        }
        this.generationMethodID = 0;
    }

    private static int storeMethodHandleLookupObject(final MethodVisitor mv, int baseVarIdx) {
        final int varIdx = baseVarIdx + 1;
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
        mv.visitVarInsn(ASTORE, varIdx);
        return varIdx;
    }

    private static void pushPrimitiveType(final MethodVisitor mv, final Type primitiveType) {
        String owner;
        switch (primitiveType.getSort()) {
            case Type.BOOLEAN:
                owner = "java/lang/Boolean";
                break;
            case Type.BYTE:
                owner = "java/lang/Byte";
                break;
            case Type.CHAR:
                owner = "java/lang/Character";
                break;
            case Type.SHORT:
                owner = "java/lang/Short";
                break;
            case Type.INT:
                owner = "java/lang/Integer";
                break;
            case Type.LONG:
                owner = "java/lang/Long";
                break;
            case Type.FLOAT:
                owner = "java/lang/Float";
                break;
            case Type.DOUBLE:
                owner = "java/lang/Double";
                break;
            case Type.VOID:
                owner = "java/lang/Void";
                break;
            default:
                throw new IllegalStateException("type '" + primitiveType.getClassName() + "' is not primitive");
        }
        mv.visitFieldInsn(GETSTATIC, owner, "TYPE", "Ljava/lang/Class;");
    }

    private static void pushType(final MethodVisitor mv, final Type type) {
        switch (type.getSort()) {
            case Type.OBJECT: //fall through
            case Type.ARRAY:
                mv.visitLdcInsn(type);
                break;
            default:
                pushPrimitiveType(mv, type);
                break;
        }
    }

    private static void pushTypeArray(final MethodVisitor mv, final Type[] types) {
        mv.visitIntInsn(BIPUSH, types.length);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        for (int i = 0; i < types.length; ++i) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(BIPUSH, i);
            pushType(mv, types[i]);
            mv.visitInsn(AASTORE);
        }
    }

    private static int storeCallSiteMethodDescriptor(final MethodVisitor mv, final Type methodType, int baseVarIdx) {
        final int varIdx = baseVarIdx + 2;
        final Type[] argTypes = methodType.getArgumentTypes();
        pushType(mv, methodType.getReturnType());
        pushTypeArray(mv, argTypes);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
        mv.visitVarInsn(ASTORE, varIdx);
        return varIdx;
    }

    private static void pushMethodTypeObject(final MethodVisitor mv, final Type methodType) {
        final Type[] argTypes = methodType.getArgumentTypes();
        pushType(mv, methodType.getReturnType());
        pushTypeArray(mv, argTypes);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
    }

    private static int storeCallSite(final MethodVisitor mv,
                                     final String name, final Handle bootstrapMethodHandle, final Object[] bootstrapMethodArguments,
                                     int lookupObjectVarIdx, int methodDescriptorVarIdx, int baseVarIdx) {
        final int varIdx = baseVarIdx + 3;

        mv.visitVarInsn(ALOAD, lookupObjectVarIdx);
        mv.visitLdcInsn(name);
        mv.visitVarInsn(ALOAD, methodDescriptorVarIdx);

        /*
         * the JLS guarantees that bootstrapMethodArguments can contain only Class, MethodHandle, MethodType, String, int, long, float, and double types
         */
        for (final Object o : bootstrapMethodArguments) {
            if (o instanceof Type) {
                final Type t = (Type) o;
                if (t.getSort() == Type.METHOD) {
                    pushMethodTypeObject(mv, t);
                } else {
                    throw new UnsupportedOperationException("Bootstrap method arguments of type Class are not yet supported");
                }
            } else if (o instanceof Handle) {
                final Handle h = (Handle) o;
                mv.visitVarInsn(ALOAD, lookupObjectVarIdx);
                mv.visitLdcInsn(Type.getType("L" + h.getOwner() + ";"));
                mv.visitLdcInsn(h.getName());
                
                final Type t = Type.getMethodType(h.getDesc());
                pushMethodTypeObject(mv, t);

                switch (h.getTag()) {
                    case H_INVOKESTATIC:
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
                        break;
                    case H_INVOKEVIRTUAL:
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
                        break;
                    default:
                        throw new UnsupportedOperationException("Method handles with a tag of '" + h.getTag() + "' are not yet supported");
                }
            } else if (o instanceof String) {
                throw new UnsupportedOperationException("Bootstrap method arguments of type String are not yet supported");
            } else if (o instanceof Integer) {
                throw new UnsupportedOperationException("Bootstrap method arguments of type Integer are not yet supported");
            } else if (o instanceof Long) {
                throw new UnsupportedOperationException("Bootstrap method arguments of type Long are not yet supported");
            } else if (o instanceof Float) {
                throw new UnsupportedOperationException("Bootstrap method arguments of type Float are not yet supported");
            } else if (o instanceof Double) {
                throw new UnsupportedOperationException("Bootstrap method arguments of type Double are not yet supported");
            } else {
                throw new IllegalStateException("Illegal type '" + o.getClass().getCanonicalName() + "' in bootstrap method argument list");
            }
        }

        switch (bootstrapMethodHandle.getTag()) {
            case H_INVOKESTATIC:
                mv.visitMethodInsn(INVOKESTATIC, bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), bootstrapMethodHandle.isInterface());
                break;
            case H_INVOKEVIRTUAL:
                mv.visitMethodInsn(INVOKEVIRTUAL, bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), bootstrapMethodHandle.isInterface());
                break;
            default:
                throw new UnsupportedOperationException("Method handles with a tag of '" + bootstrapMethodHandle.getTag() + "' are not yet supported");
        }
        mv.visitTypeInsn(CHECKCAST, "java/lang/invoke/CallSite");
        mv.visitVarInsn(ASTORE, varIdx);
        return varIdx;
    }

    private static void returnObjectProducedFromCallsiteInvocation(final MethodVisitor mv, final Type methodType, final int callsiteVarIdx) {
        mv.visitVarInsn(ALOAD, callsiteVarIdx);
//        mv.visitTypeInsn(CHECKCAST, "java/lang/invoke/CallSite");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/CallSite", "getTarget", "()Ljava/lang/invoke/MethodHandle;", false);

        final Type[] argTypes = methodType.getArgumentTypes();
        mv.visitIntInsn(BIPUSH, argTypes.length);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int i = 0, varIdx = 0; i < argTypes.length; ++i, ++varIdx) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(BIPUSH, i);
            switch (argTypes[i].getSort()) {
                case Type.BOOLEAN:
                    mv.visitVarInsn(ILOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    break;
                case Type.BYTE:
                    mv.visitVarInsn(ILOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    break;
                case Type.CHAR:
                    mv.visitVarInsn(ILOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    break;
                case Type.SHORT:
                    mv.visitVarInsn(ILOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    break;
                case Type.INT:
                    mv.visitVarInsn(ILOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(LLOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    varIdx += 1; //two slots used to store a long
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(FLOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(DLOAD, varIdx);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    varIdx += 1; //two slots used to store a double
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    mv.visitVarInsn(ALOAD, varIdx);
                    break;
                default:
                    throw new UnsupportedOperationException("Callsite method arguments of type '" + argTypes[i].getClassName() + "' are not yet supported");
            }
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false);

        final Type returnType = methodType.getReturnType();
        switch (returnType.getSort()) {
            case Type.BOOLEAN:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                mv.visitInsn(IRETURN);
                break;
            case Type.BYTE:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                mv.visitInsn(IRETURN);
                break;
            case Type.CHAR:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                mv.visitInsn(IRETURN);
                break;
            case Type.SHORT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                mv.visitInsn(IRETURN);
                break;
            case Type.INT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                mv.visitInsn(IRETURN);
                break;
            case Type.LONG:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                mv.visitInsn(LRETURN);
                break;
            case Type.FLOAT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                mv.visitInsn(FRETURN);
                break;
            case Type.DOUBLE:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                mv.visitInsn(DRETURN);
                break;
            case Type.VOID:
                mv.visitInsn(RETURN);
                break;
            case Type.OBJECT: //fall through
            case Type.ARRAY:
                mv.visitTypeInsn(CHECKCAST, returnType.getClassName().replace('.', '/'));
                mv.visitInsn(ARETURN);
                break;
            default:
                throw new UnsupportedOperationException("Callsite return values of type '" + returnType.getClassName() + "' are not yet supported");
        }
    }

    private static int calculateBaseVarIdx(final Type[] argTypes) {
        int varIdx = -1;
        for (final Type t : argTypes) {
            switch (t.getSort()) {
                case Type.DOUBLE: //fall through
                case Type.LONG:
                    varIdx += 2;
                    break;
                default:
                    varIdx += 1;
                    break;
            }
        }
        return varIdx;
    }

    private static class MethodDescriptor {

        final String name;

        final String desc;

        MethodDescriptor(final String name, final String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    private MethodDescriptor createCallsiteInvokerMethod(final String callsiteName, final String callsiteDescriptor, final Handle bootstrapMethodHandle, final Object[] bootstrapMethodArguments) {
        final String generatedName = callerMethodName + "$indy$" + generationMethodID++;
        final MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
                generatedName, callsiteDescriptor, null, null);

        mv.visitCode();
        final Type callsiteMethodType = Type.getMethodType(callsiteDescriptor);
        final int baseVarIdx = calculateBaseVarIdx(callsiteMethodType.getArgumentTypes());

        final int lookupVarIdx = storeMethodHandleLookupObject(mv, baseVarIdx);
        final int descVarIdx = storeCallSiteMethodDescriptor(mv, callsiteMethodType, baseVarIdx);
        final int callsiteVarIdx = storeCallSite(mv, callsiteName, bootstrapMethodHandle, bootstrapMethodArguments, lookupVarIdx, descVarIdx, baseVarIdx);
        returnObjectProducedFromCallsiteInvocation(mv, callsiteMethodType, callsiteVarIdx);

        mv.visitMaxs(12, 4 + baseVarIdx);
        mv.visitEnd();

        return new MethodDescriptor(generatedName, callsiteDescriptor);
    }

    @Override
    public void visitInvokeDynamicInsn(final String callsiteName, final String callsiteDescriptor, final Handle bootstrapMethodHandle, final Object[] bootstrapMethodArguments) {
        final MethodDescriptor m = createCallsiteInvokerMethod(callsiteName, callsiteDescriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, m.name, m.desc, false);
    }
}

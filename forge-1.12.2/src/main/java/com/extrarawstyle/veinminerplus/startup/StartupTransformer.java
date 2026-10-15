package com.extrarawstyle.veinminerplus.startup;

import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

public final class StartupTransformer implements IClassTransformer {
    private static final String SPLASH = "net/minecraftforge/fml/client/SplashProgress";
    private static final String TEXTURE = SPLASH + "$Texture";
    private static final String RENDERER = "com/extrarawstyle/veinminerplus/startup/StartupRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || FMLLaunchHandler.side() == null || !FMLLaunchHandler.side().isClient()
                || !(SPLASH.replace('/', '.').equals(transformedName)
                        || (SPLASH + "$2").replace('/', '.').equals(transformedName))) {
            return bytes;
        }
        try {
            ClassNode type = new ClassNode();
            new ClassReader(bytes).accept(type, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : type.methods) {
                if ("run".equals(method.name) && "()V".equals(method.desc)) {
                    changed = replaceLogo(method);
                } else if ("finish".equals(method.name) && "()V".equals(method.desc)) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        AbstractInsnNode previous = instruction.getPrevious();
                        if (isCall(instruction, TEXTURE, "delete") && previous instanceof FieldInsnNode
                                && "forgeTexture".equals(((FieldInsnNode) previous).name)) {
                            method.instructions.insert(instruction,
                                    new MethodInsnNode(Opcodes.INVOKESTATIC, RENDERER, "close", "()V", false));
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                return bytes;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException exception) {
            FMLLog.log.warn("Could not patch the Forge startup logo; keeping the original", exception);
            return bytes;
        }
    }

    private static boolean replaceLogo(MethodNode method) {
        AbstractInsnNode start = null;
        AbstractInsnNode end = null;
        FrameNode nextFrame = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (isCall(instruction, TEXTURE, "getFrames")) {
                AbstractInsnNode store = instruction.getNext();
                while (store != null && store.getOpcode() != Opcodes.ISTORE) {
                    store = store.getNext();
                }
                start = store;
            }
            if (start != null && instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (SPLASH.equals(field.owner) && "mutex".equals(field.name)) {
                    end = instruction;
                    break;
                }
            }
        }
        if (start == null || end == null) {
            return false;
        }
        for (AbstractInsnNode instruction = end; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FrameNode) {
                nextFrame = (FrameNode) instruction;
                break;
            }
        }
        int locals = ((VarInsnNode) start).var + 1;
        if (nextFrame == null || nextFrame.local.size() < locals) {
            return false;
        }
        // Preserve Forge's existing frames so no Minecraft classes are loaded to compute new ones.
        List<Object> frameLocals = nextFrame.local.subList(0, locals);
        LabelNode afterLogo = new LabelNode();
        InsnList condition = new InsnList();
        condition.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RENDERER, "draw", "()Z", false));
        condition.add(new JumpInsnNode(Opcodes.IFNE, afterLogo));
        method.instructions.insert(start, condition);
        InsnList target = new InsnList();
        target.add(afterLogo);
        target.add(new FrameNode(Opcodes.F_NEW, locals, frameLocals.toArray(), 0, new Object[0]));
        method.instructions.insertBefore(end, target);
        return true;
    }

    private static boolean isCall(AbstractInsnNode instruction, String owner, String name) {
        if (!(instruction instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        return owner.equals(call.owner) && name.equals(call.name);
    }
}

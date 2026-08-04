package com.seekrtech.robolectricarm64;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Patches {@code org.robolectric.nativeruntime.DefaultNativeRuntimeLoader#isSupported()} in a
 * nativeruntime jar so that linux+aarch64 is also accepted.
 *
 * <p>Upstream semantics are preserved: mac accepts (aarch64 || x86_64), windows accepts x86_64
 * only. The only change is that linux now accepts (x86_64 || aarch64) instead of x86_64 alone.
 */
public final class PatchLoader {

  private static final String TARGET = "org/robolectric/nativeruntime/DefaultNativeRuntimeLoader.class";
  private static final String SELF = "org/robolectric/nativeruntime/DefaultNativeRuntimeLoader";
  private static final String OS_UTIL = "org/robolectric/util/OsUtil";

  private PatchLoader() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("usage: PatchLoader <in.jar> <out.jar>");
      System.exit(1);
    }
    patch(Path.of(args[0]), Path.of(args[1]));
  }

  /** Copies {@code inJar} to {@code outJar}, rewriting the target class in place. */
  public static void patch(Path inJar, Path outJar) throws IOException {
    try (ZipFile zin = new ZipFile(inJar.toFile());
        JarOutputStream jout = new JarOutputStream(Files.newOutputStream(outJar))) {
      Enumeration<? extends ZipEntry> entries = zin.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        byte[] data;
        if (entry.getName().equals(TARGET)) {
          data = patchClass(zin.getInputStream(entry).readAllBytes());
          System.out.println("PATCHED " + TARGET + " (" + data.length + " bytes)");
        } else {
          data = zin.getInputStream(entry).readAllBytes();
        }
        // Copy constructor preserves method/time/extra/crc/sizes so output is byte-stable.
        JarEntry outEntry = new JarEntry(entry);
        jout.putNextEntry(outEntry);
        jout.write(data);
        jout.closeEntry();
      }
    }
    System.out.println("done");
  }

  private static byte[] patchClass(byte[] classBytes) {
    ClassReader cr = new ClassReader(classBytes);
    // COMPUTE_FRAMES recomputes frames for every method. Other methods may merge
    // android.* types at branch targets that this host cannot load; degrade to
    // java/lang/Object (a valid supertype, so verification still passes).
    ClassWriter cw =
        new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
          @Override
          protected String getCommonSuperClass(String type1, String type2) {
            try {
              return super.getCommonSuperClass(type1, type2);
            } catch (TypeNotPresentException e) {
              return "java/lang/Object";
            }
          }
        };
    ClassVisitor cv =
        new ClassVisitor(Opcodes.ASM9, cw) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            if (name.equals("isSupported") && desc.equals("()Z")) {
              MethodVisitor mw = super.visitMethod(access, name, desc, signature, exceptions);
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitCode() {
                  emitIsSupported(mw);
                }

                @Override
                public void visitMaxs(int maxStack, int maxLocals) {
                  // swallowed; we emit visitMaxs ourselves in visitEnd.
                }

                @Override
                public void visitEnd() {
                  mw.visitMaxs(0, 0);
                  mw.visitEnd();
                }
              };
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
          }
        };
    cr.accept(cv, 0);
    return cw.toByteArray();
  }

  /** Emits the new isSupported() body into {@code mw}. */
  private static void emitIsSupported(MethodVisitor mv) {
    Label lLinux = new Label(); // entry for the linux branch
    Label lWindows = new Label(); // entry for the windows branch
    Label lFalse = new Label();
    Label lTrue = new Label();

    // mac: (aarch64 || x86_64)
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, OS_UTIL, "isMac", "()Z", false);
    mv.visitJumpInsn(Opcodes.IFEQ, lLinux);
    emitArchCheck(mv, "aarch64", lTrue);
    emitArchCheck(mv, "x86_64", lTrue);

    // linux: (x86_64 || aarch64)   <- widened
    mv.visitLabel(lLinux);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, OS_UTIL, "isLinux", "()Z", false);
    mv.visitJumpInsn(Opcodes.IFEQ, lWindows);
    emitArchCheck(mv, "x86_64", lTrue);
    emitArchCheck(mv, "aarch64", lTrue);

    // windows: x86_64
    mv.visitLabel(lWindows);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, OS_UTIL, "isWindows", "()Z", false);
    mv.visitJumpInsn(Opcodes.IFEQ, lFalse);
    emitArchCheck(mv, "x86_64", lTrue);

    mv.visitLabel(lFalse);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitInsn(Opcodes.IRETURN);

    mv.visitLabel(lTrue);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IRETURN);
  }

  /** if (Objects.equals(arch(), archName)) goto target; */
  private static void emitArchCheck(MethodVisitor mv, String archName, Label target) {
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "arch", "()Ljava/lang/String;", false);
    mv.visitLdcInsn(archName);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/Objects",
        "equals",
        "(Ljava/lang/Object;Ljava/lang/Object;)Z",
        false);
    mv.visitJumpInsn(Opcodes.IFNE, target);
  }
}

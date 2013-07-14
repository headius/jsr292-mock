package com.github.headius.jsr292mock;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.xml.ASMContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  This class takes an XML description of JSR292 API and creates the corresponding jar.
 */
public class Mock {
  private static final String INPUT = "jsr292-mock.xml";
  
  public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
    final ZipOutputStream output = new ZipOutputStream(new FileOutputStream("jsr292-mock.jar"));
    try {  
      SAXParserFactory parserFactory = SAXParserFactory.newInstance();
      parserFactory.setNamespaceAware(true);
      SAXParser parser = parserFactory.newSAXParser();

      FileInputStream input = new FileInputStream(INPUT);
      try {
        parser.parse(input, new DefaultHandler() {
          private String className;
          private ClassWriter writer;
          private ASMContentHandler asmContentHandler;

          @Override
          public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (localName.equals("jsr292-mock")) {
              return; // skip it
            }
            if (localName.equals("class")) {
              className = atts.getValue("", "name");
              writer = new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_MAXS);
              ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                  MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                  if ((access & (Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0) {
                    return mv;
                  }
                  return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitEnd() {
                      // add a default code
                      super.visitCode();
                      super.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                      super.visitInsn(Opcodes.DUP);
                      super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException",
                        "<init>", "()V");
                      super.visitInsn(Opcodes.ATHROW);
                      
                      super.visitMaxs(-1, -1);
                      super.visitEnd();
                    }
                  };
                }
              };
              asmContentHandler = new ASMContentHandler(cv);
            }
            asmContentHandler.startElement(uri, localName, qName, atts);
          }

          @Override
          public void endElement(String uri, String localName, String qName) throws SAXException {
            if (localName.equals("jsr292-mock")) {
              return; // skip it
            }
            asmContentHandler.endElement(uri, localName, qName);
            if (localName.equals("class")) {
              ZipEntry entry = new ZipEntry(className+".class");
              try {
                output.putNextEntry(entry);
                output.write(writer.toByteArray());
              } catch (IOException e) {
                throw (SAXException)new SAXException().initCause(e);
              }

              className = null;
              writer = null;
              asmContentHandler = null;
            }
          }
        });
      } finally {
        input.close();
      }
    } finally {
      output.close();
    }
  }
}

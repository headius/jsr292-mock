package com.github.headius.jsr292mock;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.xml.SAXClassAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class creates an XML description of all JSR 292 API from an existing jdk (7 or 8). 
 */
public class XMLGen {
  private static final String OUTPUT = "jsr292-mock.xml";
  
  static StringBuilder escape(StringBuilder builder, String value) {
    for(int i=0; i<value.length(); i++) {
      char c = value.charAt(i);
      switch(c) {
      case '<':
        builder.append("&lt;");
        continue;
      case '>':
        builder.append("&gt;");
        continue;
      default:
        builder.append(c);
        continue;
      }
    }
    return builder;
  }
  
  public static void main(String[] args) throws IOException {
    String file = (args.length >=1)? args[0]: "/usr/jdk/jdk1.8.0/jre/lib/rt.jar";

    final BufferedWriter output = new BufferedWriter(new FileWriter(OUTPUT), 8192);
    
    final DefaultHandler handler = new DefaultHandler() {
      @Override
      public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        try {
          StringBuilder builder = new StringBuilder();
          builder.append('<').append(localName);
          for(int i=0; i < atts.getLength(); i++) {
            escape(builder.append(' ').append(atts.getLocalName(i)).append("=\""), atts.getValue(i)).append('"');
          }
          builder.append(">\n");
          output.append(builder);
        } catch (IOException e) {
          throw new SAXException(e);
        }
      }
      
      @Override
      public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
          output.write("</" + localName + ">\n");
        } catch (IOException e) {
          throw new SAXException(e);
        }
      }
      
      @Override
      public void characters(char[] ch, int start, int length) throws SAXException {
        try {
          output.write(ch, start, length);
        } catch (IOException e) {
          throw new SAXException(e);
        }
      }
    };
    
    try {
      
      output.write("<jsr292-mock>\n");
      
      ZipFile zipfile = new ZipFile(file);
      try {
        for(ZipEntry entry: Collections.list(zipfile.entries())) {
          String name = entry.getName();
          if (!name.endsWith(".class")) {
            continue;
          }
          if (name.startsWith("java/lang/invoke/") ||
              name.startsWith("sun/invoke/") ||
              name.equals("java/lang/ClassValue.class")) {
            InputStream stream = zipfile.getInputStream(entry);
            try {
              ClassReader reader = new ClassReader(stream);
              //String className = reader.getClassName();

              final SAXClassAdapter saxClassAdapter = new SAXClassAdapter(handler, false);
              reader.accept(new ClassVisitor(Opcodes.ASM5, saxClassAdapter) {
                private String owner;
                
                // non public class must not be removed because PolymorphicSignature is not public
                
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                  owner = name;
                  // let's pretend it's a 1.5 class
                  super.visit(Opcodes.V1_5, access, name, signature, superName, interfaces);
                }
                
                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                  // filter out non-visible field
                  if ((access & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED)) != 0) {
                    return super.visitField(access, name, desc, signature, value);
                  }
                  return null;
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                  // filter out non-visible method
                  if ((access & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED)) != 0) {
                    if ((access & (Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0) {
                      
                      // filter out invoke() and invokeExact() 
                      // because they are handled correctly only by javac 7+
                      if ((name.equals("invoke") || name.equals("invokeExact"))
                              && owner.equals("java/lang/invoke/MethodHandle")) {
                          return null;
                      }
                      
                      return super.visitMethod(access, name, desc, signature, exceptions);
                    }
                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                      @Override
                      public void visitEnd() {
                        try {
                          handler.endElement("", "code", "code");
                        } catch (SAXException e) {
                          throw new RuntimeException(e);
                        }
                        super.visitEnd();
                      }
                    };
                  }
                  return null;
                }
              }, ClassReader.SKIP_CODE);    
            } finally {
              stream.close();
            }
          }
        }  
      } finally {
        zipfile.close();
      }
      
      output.write("</jsr292-mock>\n");
      
      
    } finally {
      output.close();
    }
  }
}

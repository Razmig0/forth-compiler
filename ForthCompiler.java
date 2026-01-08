import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ForthCompiler {
    
        // Counter for generating unique .s operation labels
        private static int dotSCount = 0;
    // Assembly template (same as your Node version)
    private static StringBuilder asmCode = new StringBuilder(
        ".section .rodata\n\n" +
        "fmt:\n" +
        "\t.asciz \"%ld\\n\"\n" +   // NOTE: \\n here becomes \n in the .s file
        "\n.section .text\n" +
        ".globl _start\n" +
        "_start:\n" +
        "\tmov %rsp, %rbp\n"
    );

    public static void main(String[] args) throws Exception {
        // Usage: java ForthCompiler code.fs
        if (args.length < 1) {
            System.err.println("Usage: java ForthCompiler <source.fs>");
            System.exit(1);
        }

        String inputPath = args[0];

        // 1. Read tokens byte by byte
        List<String> tokens = readTokensByteByByte(inputPath);

        // 2. Parse each token and generate asm
        for (String token : tokens) {
            parseAndGenerate(token);
        }

        // 3. Add exit syscall
        asmCode.append(generateExit());

        // 4. Write to code.s
        Files.write(
            Paths.get("code.s"),
            asmCode.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    // --------- tokenization (byte-by-byte) ---------

    private static List<String> readTokensByteByByte(String filePath) throws IOException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (InputStream in = new FileInputStream(filePath)) {
            int b;
            while ((b = in.read()) != -1) {    // -1 = EOF
                char ch = (char) b;
                // this part is added to handle comments
                // Handle comments starting with / and \
                if (ch == '/' || ch == '\\') {
                    // Save current token if any
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    // Skip until end of line
                    while ((b = in.read()) != -1) {
                        ch = (char) b;
                        if (ch == '\n') {
                            break;
                        }
                    }
                    continue;
                }
                // whitespace?
                if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(ch);
                }
            }
        }

        // last token (if file doesn't end with whitespace)
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    // --------- code generation helpers ---------

    private static void parseAndGenerate(String token) {
        // number = one or more digits, nothing else
        boolean isNumber = token.length() > 0 &&
                        token.chars().allMatch(Character::isDigit);

        if (isNumber) {
            int n = Integer.parseInt(token);   // safe: only digits
            asmCode.append(generatePushNumberCode(n));
            return;
        }

        // If it starts with + or -, treat that explicitly as an error if you want:
        if ((token.startsWith("+") || token.startsWith("-")) && token.length() < 1) {
            System.err.println("Signed numbers are not allowed: " + token);
            return;
        }

        // Forth words
        // added part Handle Forth built-in operations
        switch (token) {
            case "dup" -> asmCode.append(generateDuplicateCode());
            case "*"   -> asmCode.append(generateMultiplyCode());
            case "."   -> asmCode.append(generatePrintCode());
            case "+"   -> asmCode.append(generateAdd());
            case "swap"-> asmCode.append(generateSwap());
            case "nip" -> asmCode.append(generateNip());
            case "tuck"-> asmCode.append(generateTuck());
            case ".s"-> asmCode.append(generateDotS());
            default    -> System.err.println("Unrecognized token: " + token);
        }
    }

    private static String generatePushNumberCode(int n) {
        return "\n    push $" + n + "\n";
    }

    private static String generateDuplicateCode() {
        return """

            pop %rax
            push %rax
            push %rax
            """;
    }

    private static String generateMultiplyCode() {
        return """

            pop %rbx
            pop %rcx
            imul %rbx, %rcx
            push %rcx
            """;
    }

    private static String generatePrintCode() {
        return """

          pop %rsi
          mov $fmt, %rdi
          xor %rax, %rax

          call printf
            """;
    }

    private static String generateExit() {
        return """

            mov $60, %rax       # syscall: exit
            mov $0, %rdi        # status = 0
            syscall
            """;
    }
    // Added part for addition operation, swap, nip, tuck, and .s operations
     private static String generateAdd() {
        return """

            popq %rbx
            popq %rax
            addq %rbx, %rax
            pushq %rax
            """;
    }
    private static String generateSwap(){
        return """

            popq %rax
            popq %rbx
            pushq %rax
            pushq %rbx
            """;
    }
    private static String generateNip(){
        return """

            popq %rax
            addq $8, %rsp
            pushq %rax
            """;
    }
    private static String generateTuck(){
        return """

            popq %rax
            popq %rbx
            pushq %rax
            pushq %rbx
            pushq %rax
            """;
    }
    private static String generateDotS() {
        // Generate a unique label for this .s operation
        String id = "_" + (dotSCount++);
        // Assembly code to print the stack contents
        return "            mov %rbp, %rbx\n" +
               "            sub $8, %rbx\n" +
               "\n" +
               "        dots_loop" + id + ":\n" +
               "            cmp %rsp, %rbx\n" +
               "            jl dots_done" + id + "\n" +
               "\n" +
               "            mov (%rbx), %rsi\n" +
               "            mov $fmt, %rdi\n" +
               "            xor %rax, %rax\n" +
               "\n" +
               "            push %rbx\n" +
               "            call printf\n" +
               "            pop %rbx\n" +
               "\n" +
               "            sub $8, %rbx\n" +
               "            jmp dots_loop"+ id + "\n" +
               "\n" +
               "        dots_done" + id + ":\n" +
               "            ";
    }
}

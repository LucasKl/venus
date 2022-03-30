package venus.assembler

import venus.assembler.pseudos.checkArgsLength
import venus.riscv.MemorySegments
import venus.riscv.Program
import venus.riscv.insts.dsl.Instruction
import venus.riscv.insts.dsl.relocators.Relocator
import venus.riscv.userStringToInt

/**
 * This singleton implements a simple two-pass assembler to transform files into programs.
 */
object Assembler {
    /**
     * Assembles the given code into an unlinked Program.
     *
     * @param text the code to assemble.
     * @return an unlinked program.
     * @see venus.linker.Linker
     * @see venus.simulator.Simulator
     */
    fun assemble(text: String): AssemblerOutput {
        val (linesWithExpandedMacros, errors) = AssemblerMacroPass(text).run()
        val (passOneProg, talInstructions, passOneErrors) = AssemblerPassTwo(linesWithExpandedMacros, errors).run()
        if (passOneErrors.isNotEmpty()) {
            return AssemblerOutput(passOneProg, passOneErrors)
        }
        val passTwoOutput = AssemblerPassThree(passOneProg, talInstructions).run()
        return passTwoOutput
    }
}

data class DebugInfo(val lineNo: Int, val line: String)
data class DebugInstruction(val debug: DebugInfo, val LineTokens: List<String>)
data class PassTwoOutput(
        val prog: Program,
        val talInstructions: List<DebugInstruction>,
        val errors: List<AssemblerError>
)
data class AssemblerOutput(val prog: Program, val errors: List<AssemblerError>)

data class AssemblerMacro(val name: String, val args: List<String>, val instructions: MutableList<String>)

/**
 * Pass #1 of our three pass assembler.
 *
 * It parses macro definitions and expands macros that are found inside [text]
 * It returns a pair containing a list of instructions after all macros are expanded and a list of errors that were
 * the code before macro expansion.
 */
internal class AssemblerMacroPass(private val text: String) {
    /** Result lines */
    val outLines : MutableList<String> = mutableListOf()
    /** Macros */
    /** Set to true when a new valid .macro definition is found. All following instructions go into macro until .endm */
    var recordingMacro = false
    /** The current macro that is being recorded */
    var currentMacro : AssemblerMacro = AssemblerMacro("", emptyList(), mutableListOf())
    /** A mapping from macro names to macro definitions */
    val macroMap : HashMap<String, AssemblerMacro> = HashMap<String, AssemblerMacro> ()
    /** List of all errors encountered */
    private val errors = ArrayList<AssemblerError>()

    fun run(): Pair<ArrayList<String>, ArrayList<AssemblerError>> {
        parseMacros()
        expandMacros()
        return ArrayList(outLines) to errors
    }

    /**
     * Parses the code in [text] for valid macro definitions and sets [macroMap] and [outLines].
     */
    private fun parseMacros() {
        var currentLineNumber = 1
        for (line in text.split('\n')) {
            try {
                val (labels, args) = Lexer.lexLine(line)
                if (args.size == 0) { // skip blank lines
                    currentLineNumber++
                    outLines.add("")
                    continue
                }

                if (args[0] == ".macro") {
                    if (recordingMacro) {
                        throw AssemblerError("Macro definition inside another macro is not allowed")
                    }

                    recordingMacro = true
                    if (args.size < 2) {
                        currentMacro = AssemblerMacro("", emptyList(), mutableListOf())
                        throw AssemblerError("Macro definition invalid: no name specified")
                    }
                    currentMacro = AssemblerMacro(args[1], args.drop(2), mutableListOf())
                    outLines.add("")
                } else if (args[0] == ".endm") {
                    if (recordingMacro) {
                        macroMap.put(currentMacro.name, currentMacro)
                        recordingMacro = false
                    } else {
                        throw AssemblerError(".endm outside of macro definition")
                    }
                    outLines.add("")
                } else if (recordingMacro) {
                    currentMacro.instructions.add(line)

                    /** Mark wrong arguments */
                    val (labels, tokens) = Lexer.lexLine(line)
                    for (token in tokens) {
                        if (token[0] == '\\' && !currentMacro.args.contains(token.drop(1))) {
                            throw AssemblerError("Macro argument " + token + " invalid")
                        }
                    }
                    outLines.add("")
                } else if (isValidMacro(args[0])) {
                    val macro = macroMap.get(args[0])
                    val arguments = args.drop(1)
                    if (macro != null && arguments.size != macro.args.size) {
                        throw AssemblerError("Macro " + macro.name + " requires " + macro.args.size + " arguments")
                    }
                    outLines.add(line)
                } else {
                    outLines.add(line)
                }
            } catch (e: AssemblerError) {
                errors.add(AssemblerError(currentLineNumber, e))

            }
            currentLineNumber++
        }
    }

    /**
     * Goes throug [outLines] and expands valid macros. Works in-place on [outLines] which is later used for the next pass
     */
    private fun expandMacros() {
        var index = 0

        while (index < outLines.size && outLines.size > 0) {
            try {
                val line = outLines[index]
                val (labels, args) = Lexer.lexLine(line)
                if (args.size == 0) { // skip blank lines
                    index++
                    continue
                }

                // If we found a valid macro expand it
                if (isValidMacro(args[0])) {
                    /** get macro from map. We know this macro exists since it passed [isValidMacro] */
                    val macro = macroMap.get(args[0])
                    val arguments = args.drop(1)
                    // remove the line from the input
                    outLines.removeAt(index)
                    // store index to later return to this place to expand nested macros
                    val storeIndex = index
                    for (instruction in macro!!.instructions) {
                        val (labels, tokens) = Lexer.lexLine(instruction)

                        /**
                         * A helper function that replaces arguments inside the macro body with the passed values
                         */
                        fun replaceMacroArg(name: String): String {
                            // not an argument, we can return
                            if (name[0] != '\\') return name

                            var argIndex = 0
                            for (argument in macro.args) {
                                // we found the correct argument. Replace with value from passed argument list
                                if (name.drop(1) == argument) {
                                    return arguments[argIndex]
                                }
                                argIndex++
                            }
                            throw AssemblerError("Macro argument invalid")
                        }

                        // join everything back to string
                        val replacedArgs = tokens.map { replaceMacroArg(it) }.joinToString(separator = " ")
                        outLines.add(index++, replacedArgs)
                    }
                    index = storeIndex - 1
                }
                index++
            } catch (e: AssemblerError) {
                errors.add(AssemblerError(++index, e))
                index++
            }
        }
    }

    /**
     * Determines if the given token is the name of a defined macro
     *
     * @param cmd the token to check
     * @return true if the token is the name of a defined macro
     * @see parseAssemblerDirective
     */
    private fun isValidMacro(cmd: String) = macroMap.containsKey(cmd)
}

/**
 * Pass #2 of our two pass assembler.
 *
 * It parses labels, expands pseudo-instructions and follows assembler directives.
 * It populations [talInstructions], which is then used by [AssemblerPassThree] in order to actually assemble the code.
 */
internal class AssemblerPassTwo(private val lines: ArrayList<String>, val errors: ArrayList<AssemblerError>) {
    /** The program we are currently assembling */
    private val prog = Program()
    /** The text offset where the next instruction will be written */
    private var currentTextOffset = MemorySegments.TEXT_BEGIN
    /** The data offset where more data will be written */
    private var currentDataOffset = MemorySegments.STATIC_BEGIN
    /** Whether or not we are currently in the text segment */
    private var inTextSegment = true
    /** TAL Instructions which will be added to the program */
    private val talInstructions = ArrayList<DebugInstruction>()
    /** The current line number (for user-friendly errors) */
    private var currentLineNumber = 0
    /** List of all errors encountered */
    //private val errors = ArrayList<AssemblerError>()

    fun run(): PassTwoOutput {
        doPassOne()
        return PassTwoOutput(prog, talInstructions, errors)
    }

    private fun doPassOne() {
        for (line in lines) {
            try {
                currentLineNumber++

                val offset = getOffset()

                val (labels, args) = Lexer.lexLine(line)
                for (label in labels) {
                    val oldOffset = prog.addLabel(label, offset)
                    if (oldOffset != null) {
                        throw AssemblerError("label $label defined twice")
                    }
                }

                if (args.isEmpty() || args[0].isEmpty()) continue // empty line

                if (isAssemblerDirective(args[0])) {
                    parseAssemblerDirective(args[0], args.drop(1), line)
                } else {
                    val expandedInsts = replacePseudoInstructions(args)
                    for (inst in expandedInsts) {
                        val dbg = DebugInfo(currentLineNumber, line)
                        talInstructions.add(DebugInstruction(dbg, inst))
                        currentTextOffset += 4
                    }
                }
            } catch (e: AssemblerError) {
                errors.add(AssemblerError(currentLineNumber, e))
            }
        }
    }

    /** Gets the current offset (either text or data) depending on where we are writing */
    fun getOffset() = if (inTextSegment) currentTextOffset else currentDataOffset

    /**
     * Determines if the given token is an assembler directive
     *
     * @param cmd the token to check
     * @return true if the token is an assembler directive
     * @see parseAssemblerDirective
     */
    private fun isAssemblerDirective(cmd: String) = cmd.startsWith(".")

    /**
     * Replaces any pseudoinstructions which occur in our program.
     *
     * @param tokens a list of strings corresponding to the space delimited line
     * @return the corresponding TAL instructions (possibly unchanged)
     */
    private fun replacePseudoInstructions(tokens: LineTokens): List<LineTokens> {
        try {
            val cmd = getInstruction(tokens)
            val pw = PseudoDispatcher.valueOf(cmd).pw
            return pw(tokens, this)
        } catch (t: Throwable) {
            /* TODO: don't use throwable here */
            /* not a pseudoinstruction, or expansion failure */
            return listOf(tokens)
        }
    }

    /**
     * Changes the assembler state in response to directives
     *
     * @param directive the assembler directive, starting with a "."
     * @param args any arguments following the directive
     * @param line the original line (which is needed for some directives)
     */
    private fun parseAssemblerDirective(directive: String, args: LineTokens, line: String) {
        when (directive) {
            ".data" -> inTextSegment = false
            ".text" -> inTextSegment = true

            ".macro" -> true
            ".endm" -> true

            ".byte" -> {
                for (arg in args) {
                    val byte = userStringToInt(arg)
                    if (byte !in -127..255) {
                        throw AssemblerError("invalid byte $byte too big")
                    }
                    prog.addToData(byte.toByte())
                    currentDataOffset++
                }
            }

            ".asciiz" -> {
                checkArgsLength(args, 1)
                val ascii: String = try {
                    JSON.parse(args[0])
                } catch (e: Throwable) {
                    throw AssemblerError("couldn't parse ${args[0]} as a string")
                }
                for (c in ascii) {
                    if (c.toInt() !in 0..127) {
                        throw AssemblerError("unexpected non-ascii character: $c")
                    }
                    prog.addToData(c.toByte())
                    currentDataOffset++
                }

                /* Add NUL terminator */
                prog.addToData(0)
                currentDataOffset++
            }

            ".string" -> {
                checkArgsLength(args, 1)
                val ascii: String = try {
                    JSON.parse(args[0])
                } catch (e: Throwable) {
                    throw AssemblerError("couldn't parse ${args[0]} as a string")
                }
                for (c in ascii) {
                    if (c.toInt() !in 0..127) {
                        throw AssemblerError("unexpected non-ascii character: $c")
                    }
                    prog.addToData(c.toByte())
                    currentDataOffset++
                }

                /* Add NUL terminator */
                prog.addToData(0)
                currentDataOffset++
            }

            ".word" -> {
                for (arg in args) {
                    val word = userStringToInt(arg)
                    prog.addToData(word.toByte())
                    prog.addToData((word shr 8).toByte())
                    prog.addToData((word shr 16).toByte())
                    prog.addToData((word shr 24).toByte())
                    currentDataOffset += 4
                }
            }

            ".globl" -> {
                args.forEach(prog::makeLabelGlobal)
            }

            ".float", ".double", ".align" -> {
                println("Warning: $directive not currently supported!")
            }

            else -> throw AssemblerError("unknown assembler directive $directive")
        }
    }

    fun addRelocation(relocator: Relocator, offset: Int, label: String) =
            prog.addRelocation(relocator, label, offset)
}

/**
 * Pass #3 of our two pass assembler.
 *
 * It writes TAL instructions to the program, and also adds debug info to the program.
 * @see addInstruction
 * @see venus.riscv.Program.addDebugInfo
 */
internal class AssemblerPassThree(val prog: Program, val talInstructions: List<DebugInstruction>) {
    private val errors = ArrayList<AssemblerError>()
    fun run(): AssemblerOutput {
        for ((dbg, inst) in talInstructions) {
            try {
                addInstruction(inst)
                prog.addDebugInfo(dbg)
            } catch (e: AssemblerError) {
                val (lineNumber, _) = dbg
                errors.add(AssemblerError(lineNumber, e))
            }
        }
        return AssemblerOutput(prog, errors)
    }

    /**
     * Adds machine code corresponding to our instruction to the program.
     *
     * @param tokens a list of strings corresponding to the space delimited line
     */
    private fun addInstruction(tokens: LineTokens) {
        if (tokens.isEmpty() || tokens[0].isEmpty()) return
        val cmd = getInstruction(tokens)
        val inst = Instruction[cmd]
        val mcode = inst.format.fill()
        inst.parser(prog, mcode, tokens.drop(1))
        prog.add(mcode)
    }
}

/**
 * Gets the instruction from a line of code
 *
 * @param tokens the tokens from the current line
 * @return the instruction (aka the first argument, in lowercase)
 */
private fun getInstruction(tokens: LineTokens) = tokens[0].toLowerCase()

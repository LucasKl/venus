package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/**
 * Writes pseudoinstruction `seq` (set equal to)
 * @todo add a settings option for "extended pseudoinstructions"
 */
object SEQ : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 4)
        checkStrictMode()
        val subtract = listOf("sub", args[1], args[2], args[3])
        val checkZero = listOf("sltiu", args[1], args[1], "1")
        return listOf(subtract, checkZero)
    }
}

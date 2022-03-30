package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/**
 * Writes pseudoinstruction `sgt` (set greater than)
 * @todo add a settings option for "extended pseudoinstructions"
 */
object SGT : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 4)
        checkStrictMode()
        val unsigned = if (args[0].endsWith("u")) "u" else ""
        return listOf(listOf("slt$unsigned", args[1], args[3], args[2]))
    }
}

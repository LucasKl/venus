package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/** Writes pseudoinstruction `ret` */
object RET : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 1)
        return listOf(listOf("jalr", "x0", "x1", "0"))
    }
}
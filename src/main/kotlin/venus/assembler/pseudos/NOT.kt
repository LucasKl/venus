package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/** Writes pseudoinstruction `not rd, rs` */
object NOT : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 3)
        return listOf(listOf("xori", args[1], args[2], "-1"))
    }
}

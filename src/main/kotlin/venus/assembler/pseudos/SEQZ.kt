package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/** Writes pseudoinstruction `seqz rd, rs` */
object SEQZ : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 3)
        return listOf(listOf("sltiu", args[1], args[2], "1"))
    }
}

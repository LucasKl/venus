package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter

/** Writes pseudoinstruction `bnez rs, label` */
object BNEZ : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 3)
        return listOf(listOf("bne", args[1], "x0", args[2]))
    }
}

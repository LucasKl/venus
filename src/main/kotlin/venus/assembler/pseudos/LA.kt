package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter
import venus.riscv.insts.dsl.relocators.PCRelHiRelocator
import venus.riscv.insts.dsl.relocators.PCRelLoRelocator

/**
 * Writes pseudoinstruction `la reg, label`.
 *
 * Uses a `auipc` / `addi` pair and adds them to the relocation table
 */
object LA : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 3)

        val auipc = listOf("auipc", args[1], "0")
        state.addRelocation(PCRelHiRelocator, state.getOffset(), args[2])

        val addi = listOf("addi", args[1], args[1], "0")
        state.addRelocation(PCRelLoRelocator, state.getOffset() + 4, args[2])

        return listOf(auipc, addi)
    }
}

package venus.assembler.pseudos

import venus.assembler.AssemblerPassTwo
import venus.assembler.AssemblerError
import venus.assembler.LineTokens
import venus.assembler.PseudoWriter
import venus.riscv.userStringToInt

/**
 * Writes pseudoinstruction `li rd, imm`.
 *
 * This either expands to an `addi` if `imm` is small or a `lui` / `addi` pair if `imm` is big.
 */
object LI : PseudoWriter() {
    override operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens> {
        checkArgsLength(args, 3)
        val imm = try {
            userStringToInt(args[2])
        } catch (e: NumberFormatException) {
            throw AssemblerError("immediate to li too large or NaN")
        }

        if (imm in -2048..2047) {
            return listOf(listOf("addi", args[1], "x0", args[2]))
        } else {
            var imm_hi = imm ushr 12
            if(imm.and(0x800) == 0x800){
                imm_hi = imm_hi.plus(1)
            }
            val imm_lo = imm and 0xFFF
            val lui = listOf("lui", args[1], imm_hi.toString())
            val addi = listOf("addi", args[1], args[1], "0x" + imm_lo.toString(16))
            return listOf(lui, addi)
        }
    }
}
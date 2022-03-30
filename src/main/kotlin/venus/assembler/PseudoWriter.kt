package venus.assembler

/**
 * Converts a pseudoinstruction into TAL instructions.
 */
abstract class PseudoWriter {
    /**
     * Transform a pseudoinstruction into TAL instructions.
     *
     * @param args the arguments originally given to the pseudoinstruction
     * @param state the assembler's state
     * @return a list of LineTokens corresponding to the TAL instruction
     */
    internal abstract operator fun invoke(args: LineTokens, state: AssemblerPassTwo): List<LineTokens>
}

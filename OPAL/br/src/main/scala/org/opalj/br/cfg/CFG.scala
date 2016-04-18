/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package br
package cfg

import scala.collection.{Set ⇒ SomeSet}

/**
 * Represents the control flow graph of a method.
 *
 * To compute a `CFG` use the [[CFGFactory]].
 *
 * ==Thread-Safety==
 * This class is thread-safe; all data is effectively immutable.
 *
 * @param method The method for which the CFG was build.
 * @param normalReturnNode The unique exit node of the control flow graph if the
 * 		method returns normally.
 * @param abnormalReturnNode The unique exit node of the control flow graph if the
 * 		method returns abnormally (throws an exception).
 * @param basicBlocks An implicit map between a program counter and its associated
 * 		[[BasicBlock]].
 * @param catchNodes List of all catch nodes. (Usually, we have one [[CatchNode]] per
 * 		[[org.opalj.br.ExceptionHandler]].
 *
 * @author Erich Wittenbeck
 * @author Michael Eichberg
 */
case class CFG(
        code:                    Code,
        normalReturnNode:        ExitNode,
        abnormalReturnNode:      ExitNode,
        private val basicBlocks: Array[BasicBlock],
        catchNodes:              Seq[CatchNode]
) {

    final def startBlock: BasicBlock = basicBlocks(0)

    /**
     * Returns the basic block to which the instruction with the given `pc` belongs.
     *
     * @param pc A valid pc.
     */
    def bb(pc: PC): BasicBlock = basicBlocks(pc)

    /**
     * Returns the set of all reachable [[CFGNode]]s of the control flow graph.
     */
    lazy val reachableBBs: SomeSet[CFGNode] = basicBlocks(0).reachable(reflexive = true)

    /**
     * Returns the set of all [[BasicBlock]]s. (I.e., the exit and catch nodes are
     * not returned.)
     *
     * @note The returned set is recomputed every time this method is called.
     */
    lazy val allBBs: Set[BasicBlock] = basicBlocks.filter(_ ne null).toSet

    /**
     * Iterates over all runtime successors of the instruction with the given pc.
     *
     * If the returned set is empty, then the instruction is either a return instruction or an
     * instruction that always causes an exception to be thrown that is not handled by
     * a handler of the respective method.
     *
     * @param pc A valid pc of an instruction of the code block from which this cfg was derived.
     */
    def successors(pc: PC): Set[PC] = {
        val bb = this.bb(pc)
        if (bb.endPC > pc) {
            // it must be - w.r.t. the code array - the next instruction
            Set(code.instructions(pc).indexOfNextInstruction(pc)(code))
        } else {
            // the set of successor can be (at the same time) a RegularBB or an ExitNode
            bb.successors.collect {
                case bb: BasicBlock ⇒ bb.startPC
                case cb: CatchNode  ⇒ cb.handlerPC
            }
        }
    }

    /**
     * Creates a new CFG where the boundaries of the basic blocks are updated given the `pcToIndex`
     * mapping. The assumption is made that the indexes are continuous.
     * If the first index (i.e., `pcToIndex(0)` is not 0, then a new basic block for the indexes
     * in {0,pcToIndex(0)} is created if necessary.
     *
     * @param lastIndex The index of the last instruction of the underlying (non-empty) code array.
     * 		I.e., if the instruction array contains one instruction then the `lastIndex` has to be
     * 		`0`.
     */
    def mapPCsToIndexes(pcToIndex: Array[PC], lastIndex: Int): CFG = {

        val bbMapping = new IdentityHashMap[CFGNode, CFGNode]()

        val newBasicBlocks = new Array[BasicBlock](lastIndex + 1)
        var lastNewBB: BasicBlock = null
        var startIndex = 0
        val requiresNewStartBlock = pcToIndex(0) > 0
        if (requiresNewStartBlock) {
            // we have added instructions at the beginning which belong to a new start bb
            lastNewBB = new BasicBlock(0)
            startIndex = pcToIndex(0)
            lastNewBB.endPC = startIndex - 1
            Arrays.fill(newBasicBlocks.asInstanceOf[Array[Object]], 0, startIndex, lastNewBB)
        }
        var startPC = 0
        val max = basicBlocks.length
        do {
            val oldBB = basicBlocks(startPC)
            var nextStartPC = oldBB.endPC + 1
            while (nextStartPC < max && {
                val nextOldBB = basicBlocks(nextStartPC)
                (nextOldBB eq null) || (nextOldBB eq oldBB)
            }) {
                nextStartPC += 1
            }
            startIndex = pcToIndex(startPC)
            val endIndex = if (nextStartPC < max) pcToIndex(nextStartPC) - 1 else lastIndex
            lastNewBB = new BasicBlock(startIndex, endIndex)
            Arrays.fill(newBasicBlocks.asInstanceOf[Array[Object]], startIndex, endIndex + 1, lastNewBB)
            bbMapping.put(oldBB, lastNewBB)
            println(oldBB+"=>"+lastNewBB)
            startPC = nextStartPC
            startIndex = endIndex + 1
        } while (startPC < max)
        if (startIndex < lastIndex)
            Arrays.fill(newBasicBlocks.asInstanceOf[Array[Object]], startIndex, lastIndex, lastNewBB)

        if (requiresNewStartBlock) {
            newBasicBlocks(0).addSuccessor(newBasicBlocks(pcToIndex(0)))
        }

        // update the catch nodes

        catchNodes.foreach { cn ⇒
            bbMapping.put(
                cn,
                new CatchNode(
                    startPC = pcToIndex(cn.startPC),
                    endPC = pcToIndex(cn.endPC + 1) - 1,
                    handlerPC = pcToIndex(cn.handlerPC),
                    cn.catchType
                )
            )
        }

        // rewire the graph

        val newNormalReturnNode = new ExitNode(normalReturn = true)
        bbMapping.put(normalReturnNode, newNormalReturnNode)
        normalReturnNode.successors.foreach { bb ⇒
            newNormalReturnNode.addSuccessor(bbMapping.get(bb))
        }

        val newAbnormalReturnNode = new ExitNode(normalReturn = false)
        bbMapping.put(abnormalReturnNode, newAbnormalReturnNode)
        abnormalReturnNode.successors.foreach { bb ⇒
            newAbnormalReturnNode.addSuccessor(bbMapping.get(bb))
        }

        bbMapping.keySet().asScala.foreach { oldBB ⇒
            val newBB = bbMapping.get(oldBB)
            oldBB.successors.foreach { oldSuccBB ⇒
                val newSuccBB = bbMapping.get(oldSuccBB)
                assert(newSuccBB ne null, s"no mapping for $oldSuccBB")
                newBB.addSuccessor(newSuccBB)
            }
            oldBB.predecessors.foreach { oldPredBB ⇒
                val newPredBB = bbMapping.get(oldPredBB)
                assert(newPredBB ne null, s"no mapping for $oldPredBB")
                newBB.addPredecessor(newPredBB)
            }
        }

        val newCatchNodes = catchNodes.map(bbMapping.get(_).asInstanceOf[CatchNode])
        assert(newCatchNodes.forall { _ ne null })
        val newCFG = CFG(
            code,
            newNormalReturnNode,
            newAbnormalReturnNode,
            newCatchNodes,
            newBasicBlocks
        )

        // let's see if we can merge the first two basic blocks
        if (requiresNewStartBlock && basicBlocks(0).predecessors.isEmpty) {
            val secondBB = newBasicBlocks(0).successors.head.asBasicBlock
            val firstBB = newBasicBlocks(0)
            firstBB.endPC = secondBB.endPC
            firstBB.setSuccessors(secondBB.successors)
        }

        newCFG
    }

    //
    // Visualization

    override def toString: String = {
        //        code:                    Code,
        //        normalReturnNode:        ExitNode,
        //        abnormalReturnNode:      ExitNode,
        //        catchNodes:              Seq[CatchNode],
        //        private val basicBlocks: Array[BasicBlock]

        val bbIds: Map[CFGNode, Int] = (
            basicBlocks.filter(_ ne null).toSet +
            normalReturnNode + abnormalReturnNode ++ catchNodes
        ).zipWithIndex.toMap
        println(bbIds)

        "CFG("+
            bbIds.map { bbId ⇒
                val (bb, id) = bbId
                s"$id: $bb"+bb.successors.map(bbIds(_)).mkString("=>{", ", ", "}")
            }.mkString("\n\t", "\n\t", "\n\t") +
            s"normalReturnNode=${bbIds(normalReturnNode)}:$normalReturnNode\n\t"+
            s"abnormalReturnNode=${bbIds(abnormalReturnNode)}:$abnormalReturnNode\n\t"+
            s"catchNodes=${catchNodes.mkString("{", ", ", "}")}\n"+
            ")"
    }

    def toDot: String = {
        val rootNodes = Set(startBlock) ++ catchNodes
        org.opalj.graphs.toDot(rootNodes)
    }
}

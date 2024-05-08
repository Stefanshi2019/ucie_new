package edu.berkeley.cs.ucie.digital
package sideband

import chisel3._
import chisel3.util._



class SidebandMessageSenderArbitor extends Module {
    val io = IO(new Bundle {
        val rdiSb = new SbMsgSubIO()
        val phySb = new SbMsgSubIO()
        val outSb = Flipped(new SbMsgSubIO())
    }
    )
    io.phySb.handshake.ready := false.B
    io.rdiSb.bits.bits := 0.U
    io.rdiSb.bits.valid := false.B
    io.phySb.bits.bits := 0.U
    io.phySb.bits.valid := false.B
    io.rdiSb.handshake.ready := false.B

    when(io.rdiSb.handshake.valid === true.B) {
        io.outSb <> io.rdiSb
    }.otherwise{
        io.outSb <> io.phySb
    }
}
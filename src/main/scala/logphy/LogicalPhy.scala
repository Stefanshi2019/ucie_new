package edu.berkeley.cs.ucie.digital
package logphy


import chisel3._
import chisel3.util._
import interfaces._
import sideband._


// /** The raw D2D interface (RDI), from the perspective of the D2D Adapter. */
// class Rdi(rdiParams: RdiParams) extends Bundle {
//   val lclk = Input(Clock())
//   val lpData = Decoupled3(Bits((8 * rdiParams.width).W)) // abnormal signal
//   val plData = Flipped(Valid(Bits((8 * rdiParams.width).W))) // abnormal signal
//   val lp_state_req = Output(PhyStateReq())
//   val lp_link_error = Output(Bool())
//   val pl_state_sts = Input(PhyState())
//   val pl_inband_pres = Input(Bool())
//   val pl_error = Input(Bool())
//   val pl_stallreq = Input(Bool())
//   val lp_stallack = Output(Bool())
//   val pl_clk_req = Input(Bool())
//   val lp_clk_ack = Output(Bool())
//   val lp_wake_req = Output(Bool())
//   val pl_wake_ack = Input(Bool())
// }

class LogicalPhy(
    afeParams: AfeParams,
    rdiParams: RdiParams,
) extends Module {
    val io = IO(new Bundle {
        val rdi = Flipped(new Rdi(rdiParams))
        val mbAfe = new MainbandAfeIo(afeParams) // do afe later
        val sbAfe = new SidebandAfeIo(afeParams)
        val rdiSb = Flipped(new SbMsgIO()) // contains tx and rx
    })
    
    val sbMsgReceiver_ready = WireInit(false.B)


    // Initalize temporarily to pass tests
    io.sbAfe.pllLock := false.B
    io.mbAfe.txData.bits := Seq.fill(afeParams.mbLanes)(0.U)

    io.rdi.plData.valid := false.B
    io.mbAfe.txData.valid := false.B
    // io.rdi.lclk := clock
    io.rdi.lpData.ready := false.B
    io.rdi.plData.bits := false.B
    io.mbAfe.rxData.ready := true.B

    // End temp initialize
    val sbMsgSender = Module(new SidebandMessageSender()) 

   


    val sbMsgSenderArbitor = Module(new SidebandMessageSenderArbitor()) 

    sbMsgSenderArbitor.io.rdiSb <> io.rdiSb.rx
    sbMsgSenderArbitor.io.phySb.handshake.valid := false.B 
    sbMsgSenderArbitor.io.phySb.opcode := Opcode.MessageWithoutData // typically MessageWithoutData == b10010
    sbMsgSenderArbitor.io.phySb.msgCode := MsgCode.Nop // typically LinkMgmt_RDI_Req == 0x01
    sbMsgSenderArbitor.io.phySb.msgSubCode := MsgSubCode.Nak // RDI/FDI_state_req state value
    sbMsgSenderArbitor.io.phySb.msgInfo := MsgInfo.RegularResponse // == RegularResponse, usually don't change
    sbMsgSenderArbitor.io.phySb.data0 := 0.U // no use for now
    sbMsgSenderArbitor.io.phySb.data1 := 0.U // no use for now
    sbMsgSenderArbitor.io.phySb.bits.ready := true.B
    sbMsgSender.io <> sbMsgSenderArbitor.io.outSb


    io.sbAfe.rxData.ready := true.B
    io.sbAfe.txData.valid := sbMsgSender.io.bits.valid
    io.sbAfe.rxEn := true.B
    io.sbAfe.txData.bits := sbMsgSender.io.bits.bits
    // println(s"sbafe width is ${io.sbAfe.txData.bits.getWidth}")
    
    val sbMsgReceiver = Module(new SidebandMessageReceiver())
    // sbMsgReceiver.io.tx.ready := true.B // always true, sample only on sbMsgReceiver.io.tx.valid 
    // sbMsgReceiver.io.opcode = Output(Opcode())
    // sbMsgReceiver.io.msgCode = Output(MsgCode())
    // sbMsgReceiver.io.msgInfo = Output(MsgInfo())
    // sbMsgReceiver.io.msgSubCode = Output(MsgSubCode())
    
    // io.rdiSb.rx.bits.bits := sbMsgReceiver.io.bits.bits 
    // io.rdiSb.rx.bits.valid := sbMsgReceiver.io.bits.valid 
    io.rdiSb.tx.bits.ready := true.B
    io.rdiSb.tx.data0 := sbMsgReceiver.io.data0
    io.rdiSb.tx.data1 := sbMsgReceiver.io.data1 
    // io.rdiSb.tx.handshake <> sbMsgReceiver.io.handshake 
    io.rdiSb.tx.handshake.valid := sbMsgReceiver.io.handshake.valid
    io.rdiSb.tx.handshake.bits := sbMsgReceiver.io.handshake.bits 
    sbMsgReceiver.io.handshake.ready := (io.rdiSb.tx.handshake.ready | sbMsgReceiver_ready)
    io.rdiSb.tx.msgCode := sbMsgReceiver.io.msgCode 
    io.rdiSb.tx.msgSubCode := sbMsgReceiver.io.msgSubCode 
    io.rdiSb.tx.msgInfo := sbMsgReceiver.io.msgInfo
    io.rdiSb.tx.opcode := sbMsgReceiver.io.opcode 

    // data1 
    
    sbMsgReceiver.io.bits.bits := io.sbAfe.rxData.bits
    sbMsgReceiver.io.bits.valid := io.sbAfe.rxData.valid

    // Connections End ------------------------------------------------------------------

    // FSM Begin ------------------------------------------------------------------
    // protocal, adaptor, and logic phy should all have their own FSM of same states and transitions
    // Refer to p250 of UCIe spec
    // Registers
    val phyState = RegInit(PhyState.reset)
    val phyState_next   = WireInit(PhyState.reset)
    phyState_next   := phyState 
    when(io.rdi.lp_state_req === PhyStateReq.nop) {
        phyState_next := phyState
    }

    val pl_state_sts_reg = RegInit(PhyState.reset)
    val pl_inband_pres_reg = RegInit(false.B)
    val pl_error_reg = RegInit(false.B)
    val pl_stallreq_reg = RegInit(false.B)
    val pl_clk_req_reg = RegInit(false.B)
    val pl_wake_ack_reg = RegInit(false.B)

    io.rdi.pl_state_sts := phyState 
    io.rdi.pl_inband_pres := pl_inband_pres_reg
    io.rdi.pl_error := pl_error_reg
    io.rdi.pl_stallreq := pl_stallreq_reg
    io.rdi.pl_clk_req := pl_clk_req_reg
    io.rdi.pl_wake_ack := pl_wake_ack_reg

    
    val sb_lock = RegInit(false.B)
    val rdi_state_req_rsp = RegInit(0.U(2.W))

    sbMsgReceiver_ready := false.B
    switch(phyState) {
        // Link Error, Disabled, Link Reset implemented below switch
        is(PhyState.reset) {
            
            // Stage 1: start - sideband detection and training

            // Stage 1: complete - sideband detection and training

            // Stage 2: start - exchange parameters on sideband and mainband training
 
           when(io.rdi.lp_state_req === PhyStateReq.active) {
                // only start dequeue sb message once is at active state
                sbMsgReceiver_ready := true.B
                // prioritize ack reply first before state transition to avoid collision
                when(sbMsgReceiver.io.handshake.valid && 
                sbMsgReceiver.io.msgCode === MsgCode.LinkMgmt_RDI_Req &&
                sbMsgReceiver.io.msgSubCode === MsgSubCode.Active) {
                        printf(p"Received phy state to active request from remote phy partner\n")
                        printf(p"Send ack\n")
                        // send ack 
                        sbMsgSenderArbitor.io.phySb.handshake.valid := true.B 
                        sbMsgSenderArbitor.io.phySb.opcode := Opcode.MessageWithoutData
                        sbMsgSenderArbitor.io.phySb.msgCode := MsgCode.LinkMgmt_RDI_Rsp
                        sbMsgSenderArbitor.io.phySb.msgSubCode := MsgSubCode.Active
                        // remember that have received state change req
                        rdi_state_req_rsp := rdi_state_req_rsp.bitSet(0.U, true.B)

                }.elsewhen (sb_lock === false.B) {
                    // send req 
                    sbMsgSenderArbitor.io.phySb.handshake.valid := true.B 
                    sbMsgSenderArbitor.io.phySb.opcode := Opcode.MessageWithoutData
                    sbMsgSenderArbitor.io.phySb.msgCode := MsgCode.LinkMgmt_RDI_Req
                    sbMsgSenderArbitor.io.phySb.msgSubCode := MsgSubCode.Active
                    sb_lock := true.B 
                    printf(p"RDI request to Active received from local request, begin initiate bringup\n")
                    printf(p"Send phy state to active request to remote phy partner\n")

                }.otherwise {
                    // wait for ack 
                    printf(p"...\n")
                    when(sbMsgReceiver.io.handshake.valid && 
                    sbMsgReceiver.io.msgCode === MsgCode.LinkMgmt_RDI_Rsp &&
                    sbMsgReceiver.io.msgSubCode === MsgSubCode.Active) {
                        // remember that have received ack
                        rdi_state_req_rsp := rdi_state_req_rsp.bitSet(1.U, true.B)
                        // unhold lock
                        sb_lock := false.B
                    }
                }
                // if received both req and ack, can move to active
                when(rdi_state_req_rsp.andR) {
                    printf(p"RDI bringup complete, phy transitioned into Active\n")
                    phyState := PhyState.active
                    rdi_state_req_rsp := 0.U
                }
            }            

            // Stage 2: complete - exchange parameters on sideband and mainband training
 
        }
        is(PhyState.active){
            // perform stall req ack for retrain , pm , linkreset, disable
            when (  
                    io.rdi.lp_state_req === PhyStateReq.retrain || 
                    io.rdi.lp_state_req === PhyStateReq.l1 ||
                    io.rdi.lp_state_req === PhyStateReq.l2 
            ) {
                // TODO: Stallreq/ack to be implemented
                when(io.rdi.lp_stallack === false.B && pl_stallreq_reg === false.B) {
                   pl_stallreq_reg := true.B
                }.elsewhen(io.rdi.lp_stallack === true.B && pl_stallreq_reg === true.B) {
                    val condition = (io.rdi.lpData.valid && io.rdi.lpData.irdy)
                    when(condition) {
                        printf(p"Assertion fail:  io.rdi.lpData.valid = ${io.rdi.lpData.valid},  io.rdi.lpData.irdy = ${ io.rdi.lpData.irdy}\n")
                    }
                    assert(!condition, "Output must be greater than input")

                    pl_stallreq_reg := false.B
                    phyState_next := PriorityMux(Seq(
                        // Direct transition, no handshake required
                        // transition to reset requires NOP
                        (io.rdi.lp_state_req === PhyStateReq.l1, PhyState.l1),
                        (io.rdi.lp_state_req === PhyStateReq.l2, PhyState.l2),
                        //(Not applicable for CXL Flit Mode with Retry in the Adapter)
                        (io.rdi.lp_state_req === PhyStateReq.retrain, PhyState.retrain)
                    ))
                }
            }

            
        }
        // DAPM unimplemented, same as L1 transition
        // l1 unsure, l1 for power management
        is(PhyState.l1) {
            when(io.rdi.lp_state_req === PhyStateReq.retrain) {
                phyState_next := PhyState.retrain
            }
        }
        is(PhyState.linkReset) {
            phyState_next := PriorityMux(Seq(
                // transition to reset requires NOP
                (io.rdi.lp_state_req === PhyStateReq.active, PhyState.active),
                (io.rdi.lp_state_req === PhyStateReq.l1, PhyState.l1),
                (io.rdi.lp_state_req === PhyStateReq.l2, PhyState.l2)
            )) 
        }
        
        is(PhyState.retrain) {
            // Adapter requests Retrain on io.rdi if any of the following events occur:
            // 1. Software writes to the Retrain bit and the Link is in Active state
            // 2. Number of CRC or parity errors detected crosses a threshold. The specific algorithm
            // for determining this is implementation specific.
            // 3. Protocol Layer requests Retrain (only applicable for Raw Mode)
            // 4. any other implementation specific condition (if applicable)
            // Physical Layer triggers a Retrain transition on io.rdi if:
            // 1. Valid framing errors are observed.
            // 2. Remote Physical Layer requests Retrain entry.
            // 3. Adapter requests Retrain

            // TODO: inputs to be considered: 1. SW retrain bit 2. number of CRC errors 3. protocol layer request, raw only
            // TODO: upon entering retrain, events:
            // 1. Propagate retrain to all adapter LSMs 

            
            when(io.rdi.lp_state_req === PhyStateReq.active) {
                phyState_next := PhyState.active
            }
        }
        is(PhyState.disabled) {
            phyState_next := PriorityMux(Seq(
                // transition to reset requires NOP
                (io.rdi.lp_state_req === PhyStateReq.active, PhyState.active),
                (io.rdi.lp_state_req === PhyStateReq.l1, PhyState.l1),
                (io.rdi.lp_state_req === PhyStateReq.l2, PhyState.l2),
                (io.rdi.lp_state_req === PhyStateReq.retrain, PhyState.retrain)

            ))  
        }
        // l2 unsure, l2 for power management
        is(PhyState.l2) {
                phyState_next := PhyState.reset
        }
        is(PhyState.linkError) {
            phyState_next := PriorityMux(Seq(
                (io.rdi.lp_state_req === PhyStateReq.active, PhyState.active),
                (io.rdi.lp_state_req === PhyStateReq.l1, PhyState.l1),
                (io.rdi.lp_state_req === PhyStateReq.l2, PhyState.l2),
                (io.rdi.lp_state_req === PhyStateReq.retrain, PhyState.retrain)
            ))
        }
    }
    // If received internal state change req to linkreset, disabled, or error
    when(io.rdi.lp_state_req === PhyStateReq.linkReset || io.rdi.lp_state_req === PhyStateReq.disabled || io.rdi.lp_linkerror) {
        // if exiting from active , must conduct stallreq/ack handshake
        when(phyState === PhyState.active && 
        (io.rdi.lp_state_req === PhyStateReq.linkReset || io.rdi.lp_state_req === PhyStateReq.disabled)
        ){
            when(io.rdi.lp_stallack === false.B && pl_stallreq_reg === false.B) {
                pl_stallreq_reg := true.B
                sb_lock := true.B
            }.elsewhen(io.rdi.lp_stallack === true.B && pl_stallreq_reg === true.B) {
                val condition = (io.rdi.lpData.valid && io.rdi.lpData.irdy)
                when(condition) {
                    printf(p"Assertion fail:  io.rdi.lpData.valid = ${io.rdi.lpData.valid},  io.rdi.lpData.irdy = ${ io.rdi.lpData.irdy}\n")
                }
                assert(!condition, "Output must be greater than input")

                pl_stallreq_reg := false.B
                sb_lock := false.B
            }
        }

        when(sb_lock === false.B) {
            sbMsgSenderArbitor.io.phySb.handshake.valid := true.B 
            sbMsgSenderArbitor.io.phySb.opcode := Opcode.MessageWithoutData // typically MessageWithoutData == b10010
            sbMsgSenderArbitor.io.phySb.msgCode := MsgCode.LinkMgmt_RDI_Req // typically LinkMgmt_RDI_Req == 0x01
            sbMsgSenderArbitor.io.phySb.msgSubCode := PriorityMux(Seq(
                (io.rdi.lp_state_req === PhyStateReq.linkReset, MsgSubCode.LinkReset),
                (io.rdi.lp_state_req === PhyStateReq.disabled, MsgSubCode.Disable),
                (io.rdi.lp_linkerror, MsgSubCode.LinkError),
            ))
            sb_lock := true.B
        }.otherwise{
            when(sbMsgReceiver.io.bits.valid === true.B) {
                when(
                    sbMsgReceiver.io.opcode === Opcode.MessageWithoutData &&
                    sbMsgReceiver.io.msgCode === MsgCode.LinkMgmt_RDI_Rsp &&
                    sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkError
                ) {
                    phyState := PhyState.linkError
                    sb_lock := false.B
                }.otherwise{
                    printf("Checkpoint 1\n")
                }
            }
        }
    }

    // If received sb message change req to linkreset, disabled, or error
    when(sbMsgReceiver.io.bits.valid === true.B) {
        when(
            sbMsgReceiver.io.opcode === Opcode.MessageWithoutData &&
            sbMsgReceiver.io.msgCode === MsgCode.LinkMgmt_RDI_Req
        ) {
            
            when(sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkReset ||
                sbMsgReceiver.io.msgSubCode === MsgSubCode.Disable ||
                sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkError
            ){
                sbMsgSenderArbitor.io.phySb.handshake.valid := true.B 
                sbMsgSenderArbitor.io.phySb.opcode := Opcode.MessageWithoutData // typically MessageWithoutData == b10010
                sbMsgSenderArbitor.io.phySb.msgCode := MsgCode.LinkMgmt_RDI_Rsp // typically LinkMgmt_RDI_Req == 0x01
                sbMsgSenderArbitor.io.phySb.msgSubCode := PriorityMux(Seq(
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkReset, MsgSubCode.LinkReset),
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.Disable, MsgSubCode.Disable),
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkError, MsgSubCode.LinkError),
                ))
                
                phyState := PriorityMux(Seq(
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkReset, PhyState.linkReset),
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.Disable, PhyState.disabled),
                    (sbMsgReceiver.io.msgSubCode === MsgSubCode.LinkError, PhyState.linkError),
                ))
            }
        }
    }

    // Following 3 states could be transitioned from any other states
    // Disabled and LinkError transition occurs also upon remote side band message request
    // TODO: sideband conditioned transitions
    // May's understanding: according to p224 priority should be LinkError>Disabled>LinkReset
    // phyState := PriorityMux(Seq(
    //     (io.rdi.lp_linkerror, PhyState.linkError),
    //     (io.rdi.lp_state_req === PhyStateReq.nop, phyState),
    //     (io.rdi.lp_state_req === PhyStateReq.linkReset, PhyState.linkReset),
    //     (io.rdi.lp_state_req === PhyStateReq.disabled, PhyState.disabled),
    //     (true.B, phyState_next)
    // ))
    // FSM End ---------------------------------------------------------------

    
    
}

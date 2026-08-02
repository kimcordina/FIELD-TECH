package com.example.fieldtechv20kc.data.constants

import com.example.fieldtechv20kc.data.model.JobType

object LegalText {
    
    fun getLegalText(jobType: JobType): String {
        return when (jobType) {
            JobType.SERVICE_REPAIR -> SERVICE_REPAIR_TEXT
            JobType.INSTALLATION_ON_LOAN -> INSTALLATION_ON_LOAN_TEXT
            JobType.INSTALLATION_PURCHASED -> INSTALLATION_PURCHASED_TEXT
        }
    }
    
    fun getLegalTitle(jobType: JobType): String {
        return when (jobType) {
            JobType.SERVICE_REPAIR -> "Service/Repair Authorisation & Acknowledgement (N. Cordina Marketing Ltd)"
            JobType.INSTALLATION_ON_LOAN -> "Loan Installation Terms & Acknowledgement (N. Cordina Marketing Ltd)"
            JobType.INSTALLATION_PURCHASED -> "Purchase Installation Terms & Acknowledgement (N. Cordina Marketing Ltd)"
        }
    }
    
    private val SERVICE_REPAIR_TEXT = """
        I, the undersigned, hereby request and authorise N. Cordina Marketing Ltd ("the Company") to carry out service and/or repair work on the equipment described in this report.

        I acknowledge and agree that:

        - The equipment remains at all times the sole property of the Company unless expressly sold to the client in writing.

        - The service/repair work will be carried out by the Company's appointed technicians to the standards deemed appropriate by the Company.

        - Replacement parts (where applicable) may be new, reconditioned, or equivalent quality, at the Company's sole discretion.

        - The Company's liability is strictly limited to the repair/service performed. No guarantee is given or implied as to the future performance of the equipment outside the Company's standard warranty terms, which I confirm I have read and accepted.

        - The Company is not responsible for any indirect, incidental, or consequential losses, including but not limited to loss of business, downtime, or product wastage, arising from use, malfunction, or unavailability of the equipment.

        - I am responsible for ensuring the equipment is used correctly and maintained in accordance with the Company's guidelines at all times. Any misuse, neglect, or failure to follow instructions voids any warranty.

        - Any additional charges (labour, parts, consumables, call-outs, etc.) communicated to me verbally or in writing by the technician or Company representatives shall be fully payable by me/the client.

        - The equipment has been inspected and tested by the Company's technician upon completion of service/repair and confirmed to be in satisfactory working order at the time of handover.

        - This authorisation and acknowledgement is legally binding and enforceable upon signature and shall apply to this and any future service/repair carried out by the Company unless expressly stated otherwise.

        By signing below, I confirm that I have read, understood, and unconditionally agree to the above terms and conditions.
    """.trimIndent()
    
    private val INSTALLATION_ON_LOAN_TEXT = """
        LOAN INSTALLATION TERMS & ACKNOWLEDGEMENT

        I, the undersigned, hereby acknowledge receipt and installation of equipment loaned to me by N. Cordina Marketing Ltd ("the Company").

        I understand and agree that:

        The equipment is and shall remain at all times the sole property of the Company.

        The equipment is provided strictly on a temporary loan basis and does not constitute a sale, lease, or transfer of ownership.

        I am fully responsible for the safe keeping, care, and proper use of the loaned equipment in accordance with the Company's instructions.

        Any damage, loss, theft, misuse, or deterioration of the equipment (other than normal wear and tear) shall be my sole responsibility, and I shall bear all costs of repair or replacement as determined by the Company.

        The equipment must be returned to the Company immediately upon request, in the same condition as received. Failure to return the equipment entitles the Company to charge the full replacement value without prejudice to any other rights or remedies.

        The Company makes no representation or warranty, express or implied, regarding the performance or suitability of the loaned equipment, and accepts no liability for any loss, damage, or expense (including downtime or product loss) arising from its use or failure.

        The Company reserves the right to enter the premises and remove the equipment at any time without notice.

        By signing below, I confirm that I have received the equipment in good working order, have read and understood the above terms, and accept full responsibility for the loaned equipment.
    """.trimIndent()
    
    private val INSTALLATION_PURCHASED_TEXT = """
        PURCHASE INSTALLATION TERMS & ACKNOWLEDGEMENT

        I, the undersigned, hereby acknowledge the purchase and installation of equipment supplied and installed by N. Cordina Marketing Ltd ("the Company").

        I understand and agree that:

        I have purchased the equipment described in this report, subject to the Company's general terms of sale, which I acknowledge and accept.

        Installation has been carried out by technicians appointed by the Company to the standards deemed appropriate by the Company.

        The equipment is covered only by the manufacturer's warranty and the Company's standard installation warranty, both of which are strictly limited in scope and duration.

        The Company shall have no liability for any indirect, incidental, or consequential losses (including downtime, lost profits, or product wastage) arising from the use or performance of the equipment.

        My responsibilities include ensuring proper operation, regular maintenance, and compliance with all user manuals, guidelines, and instructions provided. Any misuse, neglect, or failure to follow instructions shall void any warranty.

        Any further adjustments, modifications, or servicing requested after completion of installation may incur additional charges, which I agree to pay in full.

        The equipment has been tested and shown to be in satisfactory working order at the time of installation handover.

        By signing below, I confirm that the installation has been completed, that I am satisfied the equipment is in working order, and that I fully accept the warranty limitations and terms above.
    """.trimIndent()
}

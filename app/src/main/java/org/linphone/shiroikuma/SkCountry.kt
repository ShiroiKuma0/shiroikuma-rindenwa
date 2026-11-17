package org.linphone.shiroikuma

import java.util.Locale

/**
 * shiroikuma-rindenwa fork — the country a number belongs to, for the right end of a call-history
 * row (the sister dialer shows the same thing from the system call log's geocoded location, which
 * a SIP call does not have).
 *
 * A table and not a library: the only question asked is "which country is this calling code", and
 * a phone-number library costs megabytes to answer it. Longest prefix wins. The shared code +1 is
 * reported as the United States and +7 as Russia — the sub-ranges that would tell Canada or
 * Kazakhstan apart are exactly the part a table this size leaves out.
 *
 * The name is the platform's own, in the phone's language (チェコ under a Japanese locale).
 */
object SkCountry {
    private val CODES: Map<String, String> = (
        "1:US 7:RU 20:EG 27:ZA 30:GR 31:NL 32:BE 33:FR 34:ES 36:HU 39:IT 40:RO 41:CH 43:AT " +
            "44:GB 45:DK 46:SE 47:NO 48:PL 49:DE 51:PE 52:MX 53:CU 54:AR 55:BR 56:CL 57:CO " +
            "58:VE 60:MY 61:AU 62:ID 63:PH 64:NZ 65:SG 66:TH 81:JP 82:KR 84:VN 86:CN 90:TR " +
            "91:IN 92:PK 93:AF 94:LK 95:MM 98:IR 211:SS 212:MA 213:DZ 216:TN 218:LY 220:GM " +
            "221:SN 222:MR 223:ML 224:GN 225:CI 226:BF 227:NE 228:TG 229:BJ 230:MU 231:LR " +
            "232:SL 233:GH 234:NG 235:TD 236:CF 237:CM 238:CV 239:ST 240:GQ 241:GA 242:CG " +
            "243:CD 244:AO 245:GW 248:SC 249:SD 250:RW 251:ET 252:SO 253:DJ 254:KE 255:TZ " +
            "256:UG 257:BI 258:MZ 260:ZM 261:MG 262:RE 263:ZW 264:NA 265:MW 266:LS 267:BW " +
            "268:SZ 269:KM 291:ER 297:AW 298:FO 299:GL 350:GI 351:PT 352:LU 353:IE 354:IS " +
            "355:AL 356:MT 357:CY 358:FI 359:BG 370:LT 371:LV 372:EE 373:MD 374:AM 375:BY " +
            "376:AD 377:MC 378:SM 380:UA 381:RS 382:ME 383:XK 385:HR 386:SI 387:BA 389:MK " +
            "420:CZ 421:SK 423:LI 501:BZ 502:GT 503:SV 504:HN 505:NI 506:CR 507:PA 509:HT " +
            "591:BO 592:GY 593:EC 595:PY 597:SR 598:UY 670:TL 673:BN 675:PG 676:TO 679:FJ " +
            "685:WS 850:KP 852:HK 853:MO 855:KH 856:LA 880:BD 886:TW 960:MV 961:LB 962:JO " +
            "963:SY 964:IQ 965:KW 966:SA 967:YE 968:OM 970:PS 971:AE 972:IL 973:BH 974:QA " +
            "975:BT 976:MN 977:NP 992:TJ 993:TM 994:AZ 995:GE 996:KG 998:UZ"
        ).split(' ').associate { it.substringBefore(':') to it.substringAfter(':') }

    /**
     * The country of [number], or empty when it is not an international number we can place —
     * a SIP user name, a short code, a national number without its country code.
     */
    fun nameOf(number: String?): String {
        val trimmed = number?.trim().orEmpty()
        val digits = when {
            trimmed.startsWith("+") -> trimmed.drop(1)
            trimmed.startsWith("00") -> trimmed.drop(2)
            else -> return ""
        }.filter { it.isDigit() }
        if (digits.length < 7) return ""

        for (length in 3 downTo 1) {
            val region = CODES[digits.take(length)] ?: continue
            return Locale("", region).getDisplayCountry(Locale.getDefault())
        }
        return ""
    }
}

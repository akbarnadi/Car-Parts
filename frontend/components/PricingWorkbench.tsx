"use client";

import {FormEvent,useEffect,useMemo,useState} from "react";
import {api} from "@/lib/api";

const DEFAULT_FORMULA="chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther + ((chargedSupplier + chargedLogistics + chargedCustoms + chargedBank + chargedLocal + chargedOther) * markupPercent / 100) + taxAmount - discountAmount";
const rows=[
  ["Supplier","خرید از تامین‌کننده","actualSupplier","chargedSupplier"],
  ["Cargo","حمل و کارگو","actualLogistics","chargedLogistics"],
  ["Customs","گمرک و ترخیص","actualCustoms","chargedCustoms"],
  ["Bank","بانک و انتقال وجه","actualBank","chargedBank"],
  ["Local","حمل داخلی","actualLocal","chargedLocal"],
  ["Other","سایر هزینه‌ها","actualOther","chargedOther"]
] as const;

type Sheet={
  currency:string;
  actualSupplier:string;actualLogistics:string;actualCustoms:string;actualBank:string;actualLocal:string;actualOther:string;
  chargedSupplier:string;chargedLogistics:string;chargedCustoms:string;chargedBank:string;chargedLocal:string;chargedOther:string;
  markupPercent:string;taxAmount:string;discountAmount:string;exchangeRate:string;pricingFormula:string;
};
const empty:Sheet={currency:"USD",actualSupplier:"",actualLogistics:"",actualCustoms:"",actualBank:"",actualLocal:"",actualOther:"",chargedSupplier:"",chargedLogistics:"",chargedCustoms:"",chargedBank:"",chargedLocal:"",chargedOther:"",markupPercent:"10",taxAmount:"0",discountAmount:"0",exchangeRate:"",pricingFormula:DEFAULT_FORMULA};
const num=(v:string|number|undefined|null)=>Number(v||0);

function evaluate(expression:string,vars:Record<string,number>){
  const tokens=(expression.match(/[A-Za-z][A-Za-z0-9]*|\d+(?:\.\d+)?|[()+\-*/]/g)||[]);
  if(tokens.join("").replace(/\s/g,"")!==expression.replace(/\s/g,""))throw new Error("فرمول شامل کاراکتر نامعتبر است.");
  let i=0;
  function factor():number{const t=tokens[i++];if(t===undefined)throw new Error("فرمول ناقص است.");if(t==="("){const v=expr();if(tokens[i++]!==")")throw new Error("پرانتز فرمول صحیح نیست.");return v;}if(t==="-")return -factor();if(/^[0-9]/.test(t))return Number(t);if(Object.prototype.hasOwnProperty.call(vars,t))return vars[t];throw new Error(`متغیر ناشناخته: ${t}`);}
  function term():number{let v=factor();while(tokens[i]==="*"||tokens[i]==="/"){const op=tokens[i++],r=factor();v=op==="*"?v*r:v/r;}return v;}
  function expr():number{let v=term();while(tokens[i]==="+"||tokens[i]==="-"){const op=tokens[i++],r=term();v=op==="+"?v+r:v-r;}return v;}
  const out=expr();if(i!==tokens.length||!Number.isFinite(out))throw new Error("فرمول قابل محاسبه نیست.");return Math.max(0,out);
}

export default function PricingWorkbench({caseId,onQuoteSaved}:{caseId:string;onQuoteSaved:()=>void}){
  const [sheet,setSheet]=useState<Sheet>(empty),[message,setMessage]=useState(""),[saving,setSaving]=useState(false),[sendNow,setSendNow]=useState(true);
  useEffect(()=>{api.pricingWorkbook(caseId).then((x:any)=>{if(!x||!Object.keys(x).length)return;setSheet({currency:x.currency||"USD",actualSupplier:String(x.actual_supplier??""),actualLogistics:String(x.actual_logistics??""),actualCustoms:String(x.actual_customs??""),actualBank:String(x.actual_bank??""),actualLocal:String(x.actual_local??""),actualOther:String(x.actual_other??""),chargedSupplier:String(x.charged_supplier??""),chargedLogistics:String(x.charged_logistics??""),chargedCustoms:String(x.charged_customs??""),chargedBank:String(x.charged_bank??""),chargedLocal:String(x.charged_local??""),chargedOther:String(x.charged_other??""),markupPercent:String(x.markup_percent??"10"),taxAmount:String(x.tax_amount??"0"),discountAmount:String(x.discount_amount??"0"),exchangeRate:String(x.exchange_rate??""),pricingFormula:x.pricing_formula||DEFAULT_FORMULA})}).catch(()=>{})},[caseId]);
  const vars=useMemo(()=>Object.fromEntries([...rows.flatMap(r=>[[r[2],num(sheet[r[2]])],[r[3],num(sheet[r[3]])]]),["markupPercent",num(sheet.markupPercent)],["taxAmount",num(sheet.taxAmount)],["discountAmount",num(sheet.discountAmount)]]),[sheet]);
  let total=0,formulaError="";try{total=evaluate(sheet.pricingFormula,vars)}catch(e){formulaError=e instanceof Error?e.message:"فرمول نامعتبر است."}
  const actualTotal=rows.reduce((s,r)=>s+num(sheet[r[2]]),0);
  const customerBase=rows.reduce((s,r)=>s+num(sheet[r[3]]),0);
  const profit=total-actualTotal;
  const margin=total>0?(profit/total)*100:0;
  const formulaWithValues=sheet.pricingFormula.replace(/\b[A-Za-z][A-Za-z0-9]*\b/g,(name)=>Object.prototype.hasOwnProperty.call(vars,name)?String(Number(vars[name].toFixed(4))):name);
  function set<K extends keyof Sheet>(k:K,v:Sheet[K]){setSheet(s=>({...s,[k]:v}))}
  function payload(){return {currency:sheet.currency,actualSupplier:num(sheet.actualSupplier),actualLogistics:num(sheet.actualLogistics),actualCustoms:num(sheet.actualCustoms),actualBank:num(sheet.actualBank),actualLocal:num(sheet.actualLocal),actualOther:num(sheet.actualOther),chargedSupplier:num(sheet.chargedSupplier),chargedLogistics:num(sheet.chargedLogistics),chargedCustoms:num(sheet.chargedCustoms),chargedBank:num(sheet.chargedBank),chargedLocal:num(sheet.chargedLocal),chargedOther:num(sheet.chargedOther),markupPercent:num(sheet.markupPercent),taxAmount:num(sheet.taxAmount),discountAmount:num(sheet.discountAmount),exchangeRate:sheet.exchangeRate?num(sheet.exchangeRate):null,pricingFormula:sheet.pricingFormula,calculatedTotal:total,grossProfit:profit}}
  async function save(){if(formulaError)return;setSaving(true);setMessage("");try{await api.savePricingWorkbook(caseId,payload());setMessage("صفحه محاسبات ذخیره شد.")}catch(e){setMessage(e instanceof Error?e.message:"خطا در ذخیره")}finally{setSaving(false)}}
  async function createQuote(e:FormEvent){e.preventDefault();if(formulaError)return;setSaving(true);setMessage("");try{await api.savePricingWorkbook(caseId,payload());await api.addCustomerQuote(caseId,{currency:sheet.currency,supplierCost:num(sheet.chargedSupplier),logisticsCost:num(sheet.chargedLogistics),customsCost:num(sheet.chargedCustoms),bankCost:num(sheet.chargedBank),localCost:num(sheet.chargedLocal),otherCost:num(sheet.chargedOther),markupAmount:customerBase*num(sheet.markupPercent)/100,discountAmount:num(sheet.discountAmount),taxAmount:num(sheet.taxAmount),exchangeRate:sheet.exchangeRate?num(sheet.exchangeRate):null,totalPrice:total,sendNow});setMessage(sendNow?"قیمت محاسبه و برای مشتری ثبت شد.":"پیش‌نویس قیمت ثبت شد.");onQuoteSaved()}catch(e){setMessage(e instanceof Error?e.message:"خطا در ثبت قیمت")}finally{setSaving(false)}}

  return <section className="section pricingWorkbench">
    <div className="sectionHead"><div><span className="eyebrow">Pricing workbook</span><h2>محاسبه قیمت مثل صفحه اکسل</h2><p>هزینه واقعی شرکت و مبلغی که برای مشتری منظور می‌شود جدا هستند؛ اختلاف هر ردیف، قیمت نهایی و سود به‌صورت لحظه‌ای محاسبه می‌شوند.</p></div></div>
    <form onSubmit={createQuote}>
      <div className="pricingTopbar"><label className="miniField"><span>ارز</span><input dir="ltr" maxLength={3} value={sheet.currency} onChange={e=>set("currency",e.target.value.toUpperCase())} placeholder="USD"/></label><label className="miniField"><span>نرخ تبدیل</span><div className="unitInput"><input dir="ltr" type="number" min="0" step="0.000001" value={sheet.exchangeRate} onChange={e=>set("exchangeRate",e.target.value)} placeholder="مثال: 600000"/><i>ریال / {sheet.currency}</i></div></label><label className="miniField"><span>درصد سود</span><div className="unitInput"><input dir="ltr" type="number" min="0" max="100" step="0.01" value={sheet.markupPercent} onChange={e=>set("markupPercent",e.target.value)} placeholder="مثال: 10"/><i>% (عدد ۰ تا ۱۰۰)</i></div></label></div>

      <div className="pricingTableWrap"><table className="pricingTable"><thead><tr><th>ردیف</th><th>هزینه واقعی شرکت</th><th>مبلغ منظورشده برای مشتری</th><th>اختلاف</th></tr></thead><tbody>{rows.map(r=>{const a=num(sheet[r[2]]),c=num(sheet[r[3]]);return <tr key={r[0]}><td><b>{r[1]}</b><small>{r[0]}</small></td><td><div className="tableInput"><input dir="ltr" type="number" min="0" step="0.01" value={sheet[r[2]]} onChange={e=>set(r[2],e.target.value)} placeholder="مثال: 100"/><span>{sheet.currency}</span></div></td><td><div className="tableInput"><input dir="ltr" type="number" min="0" step="0.01" value={sheet[r[3]]} onChange={e=>set(r[3],e.target.value)} placeholder="مثال: 120"/><span>{sheet.currency}</span></div></td><td className={c-a>=0?"positive":"negative"} dir="ltr">{(c-a).toFixed(2)} {sheet.currency}</td></tr>})}</tbody><tfoot><tr><td>جمع پایه</td><td dir="ltr">{actualTotal.toFixed(2)} {sheet.currency}</td><td dir="ltr">{customerBase.toFixed(2)} {sheet.currency}</td><td className={customerBase-actualTotal>=0?"positive":"negative"} dir="ltr">{(customerBase-actualTotal).toFixed(2)} {sheet.currency}</td></tr></tfoot></table></div>

      <div className="formulaExtras" style={{marginTop:16}}><label className="field"><span>مالیات / هزینه افزوده</span><div className="unitInput"><input dir="ltr" type="number" min="0" step="0.01" value={sheet.taxAmount} onChange={e=>set("taxAmount",e.target.value)} placeholder="مثال: 25"/><i>{sheet.currency}</i></div></label><label className="field"><span>تخفیف</span><div className="unitInput"><input dir="ltr" type="number" min="0" step="0.01" value={sheet.discountAmount} onChange={e=>set("discountAmount",e.target.value)} placeholder="مثال: 10"/><i>{sheet.currency}</i></div></label></div>

      <div className="pricingResult"><div><span>هزینه واقعی</span><b dir="ltr">{actualTotal.toFixed(2)} {sheet.currency}</b></div><div><span>قیمت نهایی مشتری</span><b dir="ltr">{total.toFixed(2)} {sheet.currency}</b></div><div><span>سود ناخالص</span><b className={profit>=0?"positive":"negative"} dir="ltr">{profit.toFixed(2)} {sheet.currency}</b></div><div><span>حاشیه سود</span><b className={margin>=0?"positive":"negative"} dir="ltr">{margin.toFixed(1)}%</b></div></div>

      <div className="pricingActions"><label className="checkLine"><input type="checkbox" checked={sendNow} onChange={e=>setSendNow(e.target.checked)}/> ثبت به عنوان قیمت ارسال‌شده برای مشتری</label><div><button type="button" className="secondaryButton" disabled={saving||!!formulaError} onClick={save}>ذخیره محاسبات</button><button className="primaryButton" disabled={saving||!!formulaError}>ثبت قیمت مشتری</button></div></div>
      {message&&<div className={message.includes("خطا")?"errorBanner":"successBanner"}>{message}</div>}

      <div className="formulaGrid" style={{marginTop:24}}><div className="formulaEditor"><label><span>فرمول قیمت نهایی</span><textarea dir="ltr" value={sheet.pricingFormula} onChange={e=>set("pricingFormula",e.target.value)} spellCheck={false}/></label><div className="formulaHelp">متغیرهای مجاز: <code>chargedSupplier</code> <code>chargedLogistics</code> <code>chargedCustoms</code> <code>chargedBank</code> <code>chargedLocal</code> <code>chargedOther</code> <code>markupPercent</code> <code>taxAmount</code> <code>discountAmount</code>. عملگرها: + − × ÷ و پرانتز.</div>{formulaError&&<div className="errorBanner">{formulaError}</div>}<button type="button" className="textButton" onClick={()=>set("pricingFormula",DEFAULT_FORMULA)}>بازگردانی فرمول پیش‌فرض</button></div>
        <div className="formulaEditor"><span>فرمول با مقادیر فعلی</span><div dir="ltr" style={{marginTop:10,padding:14,border:"1px solid var(--line)",borderRadius:12,background:"var(--surface-2)",overflowX:"auto",fontFamily:"monospace",lineHeight:1.9}}>{formulaWithValues}<br/><b>= {total.toFixed(2)} {sheet.currency}</b></div><small className="muted" style={{display:"block",marginTop:8}}>هر تغییر در جدول، درصد سود، مالیات یا تخفیف بلافاصله در این فرمول عددی نمایش داده می‌شود.</small></div>
      </div>
    </form>
  </section>;
}

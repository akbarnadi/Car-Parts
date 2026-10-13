"use client";
import Link from "next/link";
import {useEffect,useMemo,useState} from "react";
import {api} from "@/lib/api";
import {AuthMe,WorkflowTag} from "@/types/platform";

const stageFa:Record<string,string>={REQUESTED:"درخواست جدید",PRODUCT_CREATED:"محصول مشخص شد",SOURCING:"استعلام",SUPPLIER_QUOTED:"قیمت تامین‌کننده",INTERNAL_COSTED:"محاسبه داخلی",CUSTOMER_QUOTED:"قیمت مشتری",CUSTOMER_AGREED:"توافق",SUPPLIER_ORDERED:"خرید خارجی",IN_TRANSIT:"در حمل",CUSTOMS:"گمرک",LOCAL_WAREHOUSE:"ورود محلی",READY_FOR_DELIVERY:"آماده تحویل",DELIVERED:"تحویل",CLOSED:"بسته",CANCELLED:"لغو"};
const priceFa:Record<string,string>={VALID:"قیمت معتبر",MISSING:"بدون قیمت",EXPIRED:"قیمت منقضی"};

export default function AdminOperations(){
  const [cases,setCases]=useState<any[]>([]),[tags,setTags]=useState<WorkflowTag[]>([]),[outbox,setOutbox]=useState<any[]>([]),[me,setMe]=useState<AuthMe|null>(null),[q,setQ]=useState(""),[error,setError]=useState("");
  async function load(){try{const [c,t,o,m]=await Promise.all([api.adminCases(),api.adminTags(),api.notificationOutbox(),api.me()]);setCases(c);setTags(t);setOutbox(o);setMe(m)}catch(e){setError(e instanceof Error?e.message:"خطا")}}
  useEffect(()=>{load()},[]);
  const canOpenManager=me?.roles.some(r=>r==="SUPER_ADMIN"||r==="MANAGER")??false;
  const filtered=useMemo(()=>{const s=q.trim().toLowerCase();return s?cases.filter(x=>{const c=x.caseInfo;return [c.caseNo,c.requestNo,c.contactName,c.contactPhone,c.requestedPartNumber,c.requestText,c.vehicleText].some(v=>String(v??"").toLowerCase().includes(s))}):cases},[cases,q]);
  async function addTag(caseId:string,tagId:string){if(!tagId)return;try{await api.adminAddTag(caseId,tagId);await load()}catch(e){setError(e instanceof Error?e.message:"خطا")}}
  async function removeTag(caseId:string,tag:any){if(tag.source!=="MANUAL")return;try{await api.adminRemoveTag(caseId,tag.id);await load()}catch(e){setError(e instanceof Error?e.message:"خطا")}}
  async function sent(id:string){try{await api.markOutboxSent(id);await load()}catch(e){setError(e instanceof Error?e.message:"خطا")}}

  return <div className="pageShell"><div className="pageTitle"><span className="eyebrow">پنل ادمین</span><h1>درخواست‌ها و اقدام بعدی</h1><p>ادمین اطلاعات مشتری، محصول، اعتبار قیمت، برچسب‌ها و اعلان‌های خروجی را می‌بیند؛ هزینه‌های محرمانه تامین‌کننده در این صفحه نمایش داده نمی‌شود.</p></div>
    {error&&<div className="errorBanner">{error}</div>}
    <div className="toolbar"><input className="searchBox" value={q} onChange={e=>setQ(e.target.value)} placeholder="شماره پرونده، مشتری، تلفن، شماره فنی…"/><span>{filtered.length} پرونده</span></div>
    <div className="cardGrid">{filtered.map(x=>{const c=x.caseInfo;return <article className="contentCard" key={c.id}><div style={{display:"flex",justifyContent:"space-between",gap:12}}><div><span className="eyebrow">پرونده #{c.caseNo}</span><h3>{c.contactName||"مشتری"}</h3></div><span className={`status ${x.priceState==="VALID"?"status-active":"status-inactive"}`}>{priceFa[x.priceState]??x.priceState}</span></div><p><b>مرحله:</b> {stageFa[c.stage]??c.stage}</p><p><b>تلفن:</b> <span dir="ltr">{c.contactPhone||"—"}</span></p><p><b>شماره فنی:</b> <span dir="ltr">{c.requestedPartNumber||"—"}</span></p><p>{c.requestText}</p>{x.currentPrice&&<div className="successBanner"><b dir="ltr">{Number(x.currentPrice.amount).toLocaleString()} {x.currentPrice.currency}</b><br/><small>معتبر تا {new Date(x.currentPrice.validUntil).toLocaleDateString("fa-IR")}</small></div>}<div className="tagRow">{c.tags.map((t:any)=><button className="tag" key={t.id} onClick={()=>removeTag(c.id,t)} title={t.source==="MANUAL"?"حذف برچسب دستی":"برچسب سیستمی"}>{t.labelFa}{t.source==="MANUAL"?" ×":""}</button>)}</div><select style={{marginTop:12}} defaultValue="" onChange={e=>{addTag(c.id,e.target.value);e.currentTarget.value=""}}><option value="">+ برچسب دستی</option>{tags.filter(t=>t.source==="AVAILABLE").map(t=><option value={t.id} key={t.id}>{t.labelFa}</option>)}</select><div className="rowActions" style={{marginTop:16}}>{c.productId?<Link href={`/admin/products/${c.productId}/operations`}>قیمت و سابقه محصول</Link>:<Link href="/admin/product-requests">شناسایی محصول</Link>}{canOpenManager&&<Link href={`/manager/cases/${c.id}`}>پرونده کامل مدیر</Link>}</div></article>})}{!filtered.length&&<div className="emptyInset">پرونده‌ای پیدا نشد.</div>}</div>

    <section className="section"><div className="sectionHead"><div><h2>صف اعلان مشتریان خارج از سایت</h2><p>برای مشتری بدون حساب، پیام در صف دستی/SMS/WhatsApp/Email ثبت می‌شود تا بعداً سرویس ارسال واقعی متصل شود.</p></div></div><div className="productTableWrap"><table className="productTable"><thead><tr><th>گیرنده</th><th>کانال</th><th>پیام</th><th>وضعیت</th><th></th></tr></thead><tbody>{outbox.map(n=><tr key={n.id}><td dir="ltr">{n.recipient_phone||n.recipient_email||"—"}</td><td>{n.channel}</td><td><b>{n.title}</b><br/><small>{n.body}</small></td><td>{n.status}</td><td>{n.status==="QUEUED"&&<button className="textButton" onClick={()=>sent(n.id)}>ارسال شد</button>}</td></tr>)}{!outbox.length&&<tr><td colSpan={5}><div className="emptyInset">صف اعلان دستی خالی است.</div></td></tr>}</tbody></table></div></section>
  </div>
}

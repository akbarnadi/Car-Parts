"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {useParams} from "next/navigation";
import {api} from "@/lib/api";

const stage:Record<string,string>={REQUESTED:"درخواست ثبت شده",PRODUCT_CREATED:"محصول شناسایی شده",SOURCING:"در حال استعلام",SUPPLIER_QUOTED:"قیمت تامین‌کننده دریافت شد",INTERNAL_COSTED:"محاسبه داخلی",CUSTOMER_QUOTED:"قیمت برای شما ارسال شد",CUSTOMER_AGREED:"توافق ثبت شد",SUPPLIER_ORDERED:"خرید از تامین‌کننده",IN_TRANSIT:"در حال حمل",CUSTOMS:"گمرک",LOCAL_WAREHOUSE:"ورود به انبار",READY_FOR_DELIVERY:"آماده تحویل",DELIVERED:"تحویل شده",CLOSED:"بسته",CANCELLED:"لغو شده"};
const eventLabel:Record<string,string>={REQUEST_CREATED:"درخواست ثبت شد",PRODUCT_ATTACHED:"محصول به درخواست متصل شد",STAGE_CHANGED:"مرحله پرونده تغییر کرد",SUPPLIER_QUOTE_ADDED:"قیمت تامین‌کننده ثبت شد",CUSTOMER_QUOTE_SENT:"قیمت برای مشتری ارسال شد",CUSTOMER_QUOTE_DRAFTED:"پیش‌نویس قیمت ایجاد شد",ORDER_CREATED:"سفارش ایجاد شد",SHIPMENT_CREATED:"مرسوله ایجاد شد",PRICE_ROUTE:"بررسی وضعیت قیمت",SYSTEM_ROUTING:"بررسی سیستمی درخواست"};

export default function RequestDetail(){
  const params=useParams<{id:string}>();
  const [data,setData]=useState<any>(null),[error,setError]=useState("");
  useEffect(()=>{if(params.id)api.panelRequest(params.id).then(setData).catch(e=>setError(e.message))},[params.id]);
  if(error)return <div className="pageShell"><Link href="/panel/requests" className="muted">← درخواست‌ها</Link><div className="errorBanner">{error}</div></div>;
  if(!data)return <div className="pageShell"><div className="loadingCard">در حال بارگذاری درخواست…</div></div>;
  const r=data.request;
  const latest=data.quotes?.[0];
  return <div className="pageShell">
    <div className="pageTitleRow"><div><Link href="/panel/requests" className="muted">← درخواست‌ها</Link><h1>درخواست #{r.request_no}</h1><p>{r.request_text}</p></div><span className="status">{stage[r.workflow_stage]??r.request_status}</span></div>

    <div className="dashboardGrid">
      <article className="dashboardCard"><h3>قطعه</h3><b>{r.product_name_fa||"در حال شناسایی"}</b><p dir="ltr">{r.requested_part_number||r.real_part_number||"—"}</p>{r.product_code&&<small dir="ltr">کد محصول: {r.product_code}</small>}</article>
      <article className="dashboardCard"><h3>وضعیت پرونده</h3><b>{stage[r.workflow_stage]??r.workflow_stage??r.request_status}</b><p>آخرین تغییر: {new Date(r.workflow_updated_at||r.updated_at).toLocaleString("fa-IR")}</p></article>
      <article className="dashboardCard"><h3>آخرین قیمت</h3>{latest?<><b dir="ltr">{Number(latest.total_price).toLocaleString()} {latest.currency}</b><p>{latest.valid_until?`معتبر تا ${new Date(latest.valid_until).toLocaleDateString("fa-IR")}`:"بدون تاریخ انقضا"}</p></>:<p className="muted">هنوز قیمت نهایی برای شما ارسال نشده است.</p>}</article>
    </div>

    <section className="section"><div className="sectionHead"><div><h2>قیمت‌های ارسال‌شده</h2><p>فقط قیمت‌هایی که برای شما ارسال شده‌اند در این بخش نمایش داده می‌شوند.</p></div></div><div className="productTableWrap"><table className="productTable"><thead><tr><th>شماره پیشنهاد</th><th>مبلغ</th><th>وضعیت</th><th>اعتبار</th><th>تاریخ</th></tr></thead><tbody>{data.quotes?.map((q:any)=><tr key={q.id}><td>#{q.quote_no}</td><td><b dir="ltr">{Number(q.total_price).toLocaleString()} {q.currency}</b></td><td>{q.status}</td><td>{q.valid_until?new Date(q.valid_until).toLocaleDateString("fa-IR"):"—"}</td><td>{new Date(q.created_at).toLocaleDateString("fa-IR")}</td></tr>)}{!data.quotes?.length&&<tr><td colSpan={5}><div className="emptyInset">هنوز قیمتی ارسال نشده است.</div></td></tr>}</tbody></table></div></section>

    {data.orders?.length>0&&<section className="section"><div className="sectionHead"><div><h2>سفارش مرتبط</h2></div></div><div className="productTableWrap"><table className="productTable"><thead><tr><th>شماره سفارش</th><th>مبلغ</th><th>پرداخت‌شده</th><th>وضعیت</th><th></th></tr></thead><tbody>{data.orders.map((o:any)=><tr key={o.id}><td>#{o.order_no}</td><td dir="ltr">{Number(o.total_amount).toLocaleString()} {o.currency}</td><td dir="ltr">{Number(o.paid_amount||0).toLocaleString()} {o.currency}</td><td>{o.status}</td><td><Link className="textButton" href={`/panel/orders/${o.id}`}>مشاهده سفارش ←</Link></td></tr>)}</tbody></table></div></section>}

    <section className="section"><div className="sectionHead"><div><h2>تاریخچه پیگیری</h2><p>تغییرات قابل مشاهده مشتری در مسیر درخواست.</p></div></div><div className="tableCard">{data.timeline?.map((e:any,i:number)=><div key={i} style={{padding:14,borderBottom:"1px solid var(--line)"}}><b>{eventLabel[e.event_type]??e.event_type}</b>{(e.from_stage||e.to_stage)&&<span className="muted"> — {e.from_stage?stage[e.from_stage]??e.from_stage:""}{e.to_stage?` ← ${stage[e.to_stage]??e.to_stage}`:""}</span>}<small style={{display:"block",marginTop:4}}>{new Date(e.created_at).toLocaleString("fa-IR")}</small></div>)}{!data.timeline?.length&&<div className="emptyInset">هنوز رویدادی ثبت نشده است.</div>}</div></section>
  </div>
}

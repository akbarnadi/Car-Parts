"use client";

import Link from "next/link";
import {useEffect,useState} from "react";
import {api} from "@/lib/api";

const orderFa:Record<string,string>={PENDING_PAYMENT:"منتظر پرداخت",CONFIRMED:"تایید شده",PROCUREMENT:"در حال خرید",IN_TRANSIT:"در حال حمل",CUSTOMS:"گمرک",READY:"آماده تحویل",DELIVERED:"تحویل شده",CANCELLED:"لغو"};
const shipFa:Record<string,string>={PREPARING:"آماده‌سازی",PICKED_UP:"تحویل به حمل",IN_TRANSIT:"در مسیر",ARRIVED_COUNTRY:"ورود به کشور",CUSTOMS:"گمرک",RELEASED:"ترخیص",LOCAL_DELIVERY:"تحویل داخلی",DELIVERED:"تحویل شده"};
const stageFa:Record<string,string>={REQUESTED:"درخواست",PRODUCT_CREATED:"محصول شناسایی شد",SOURCING:"استعلام",SUPPLIER_QUOTED:"قیمت تامین‌کننده",INTERNAL_COSTED:"محاسبه داخلی",CUSTOMER_QUOTED:"قیمت مشتری",CUSTOMER_AGREED:"توافق",SUPPLIER_ORDERED:"خرید خارجی",IN_TRANSIT:"حمل",CUSTOMS:"گمرک",LOCAL_WAREHOUSE:"ورود محلی",READY_FOR_DELIVERY:"آماده تحویل",DELIVERED:"تحویل",CLOSED:"بسته",CANCELLED:"لغو"};

export default function OrderDetail({params}:{params:Promise<{id:string}>}){
  const [id,setId]=useState("");
  const [data,setData]=useState<any>(null),[error,setError]=useState("");
  useEffect(()=>{params.then(p=>setId(p.id))},[params]);
  useEffect(()=>{if(id)api.panelOrder(id).then(setData).catch(e=>setError(e.message))},[id]);
  if(!data)return <div className="pageShell">{error?<div className="errorBanner">{error}</div>:<div className="loadingCard">در حال بارگذاری سفارش…</div>}</div>;
  const o=data.order;
  const progress=Math.min(100,Math.max(0,((Number(o.paid_amount)||0)/(Number(o.total_amount)||1))*100));
  return <div className="pageShell orderDetailPage">
    <div className="pageTitleRow"><div><Link href="/panel/orders" className="muted">← سفارش‌ها</Link><h1>سفارش #{o.order_no}</h1><p>جزئیات خرید، پرداخت، حمل و رویدادهای پرونده.</p></div><span className="status large">{orderFa[o.status]??o.status}</span></div>
    <div className="orderSummaryStrip"><div><span>مبلغ کل</span><b dir="ltr">{o.total_amount} {o.currency}</b></div><div><span>پرداخت‌شده</span><b dir="ltr">{o.paid_amount} {o.currency}</b></div><div><span>مرحله پرونده</span><b>{stageFa[o.stage]??o.stage??"—"}</b></div><div><span>تاریخ</span><b>{new Date(o.created_at).toLocaleDateString("fa-IR")}</b></div></div>
    <div className="paymentProgress"><span style={{width:`${progress}%`}}/></div>

    <section className="section"><div className="sectionHead"><h2>اقلام سفارش</h2></div><div className="productTableWrap"><table className="productTable"><thead><tr><th>قطعه</th><th>شماره فنی</th><th>تعداد</th><th>قیمت واحد</th><th>جمع</th></tr></thead><tbody>{data.items.map((x:any)=><tr key={x.id}><td>{x.description_snapshot}</td><td dir="ltr">{x.part_number_snapshot||"—"}</td><td dir="ltr">{x.quantity}</td><td dir="ltr">{x.unit_price} {o.currency}</td><td dir="ltr">{(Number(x.quantity)*Number(x.unit_price)).toLocaleString()} {o.currency}</td></tr>)}</tbody></table></div></section>

    <div className="detailColumns">
      <section className="section detailPanel"><div className="sectionHead"><h2>پرداخت‌ها</h2></div>{data.payments.length?data.payments.map((p:any)=><div className="detailRow" key={p.id}><div><b dir="ltr">{p.amount} {p.currency}</b><small>{p.method||"روش ثبت نشده"}</small></div><span className="status">{p.status}</span></div>):<div className="emptyInset">پرداختی ثبت نشده است.</div>}</section>
      <section className="section detailPanel"><div className="sectionHead"><h2>حمل و رهگیری</h2></div>{data.shipments.length?data.shipments.map((s:any)=><div className="detailRow" key={s.id}><div><b>{s.carrier||"حمل‌کننده ثبت نشده"}</b><small dir="ltr">{s.tracking_number||"بدون کد رهگیری"}</small><small>{s.eta?`زمان تقریبی: ${new Date(s.eta).toLocaleDateString("fa-IR")}`:""}</small></div><span className="status">{shipFa[s.status]??s.status}</span></div>):<div className="emptyInset">هنوز اطلاعات حمل ثبت نشده است.</div>}</section>
    </div>

    <section className="section"><div className="sectionHead"><h2>تاریخچه سفارش</h2></div><div className="timelineList">{data.timeline.map((e:any,i:number)=><div className="timelineItem" key={i}><span className="timelineDot"/><div><b>{e.note||e.event_type}</b><small>{e.from_stage&&e.to_stage?`${stageFa[e.from_stage]??e.from_stage} ← ${stageFa[e.to_stage]??e.to_stage}`:""}</small><small>{new Date(e.created_at).toLocaleString("fa-IR")}</small></div></div>)}</div></section>
  </div>;
}

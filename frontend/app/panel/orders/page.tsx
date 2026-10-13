"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {api} from "@/lib/api";
const fa:Record<string,string>={PENDING_PAYMENT:"منتظر پرداخت",CONFIRMED:"تایید شده",PROCUREMENT:"در حال خرید",IN_TRANSIT:"در حال حمل",CUSTOMS:"گمرک",READY:"آماده تحویل",DELIVERED:"تحویل شده",CANCELLED:"لغو"};
export default function Orders(){
  const [rows,setRows]=useState<any[]>([]),[error,setError]=useState("");
  useEffect(()=>{api.panelOrders().then(setRows).catch(e=>setError(e.message))},[]);
  return <div className="pageShell"><div className="pageTitleRow"><div><Link className="muted" href="/panel">← پنل</Link><h1>سفارش‌ها</h1><p>روی هر سفارش بزنید تا اقلام، پرداخت‌ها، حمل و تاریخچه را ببینید.</p></div><Link className="secondaryButton" href="/order">درخواست قطعه</Link></div>{error&&<div className="errorBanner">{error}</div>}<div className="productTableWrap"><table className="productTable"><thead><tr><th>سفارش</th><th>مبلغ</th><th>پرداخت‌شده</th><th>وضعیت</th><th>تاریخ</th><th/></tr></thead><tbody>{rows.map(o=><tr key={o.id} className="clickableRow"><td><Link href={`/panel/orders/${o.id}`}><b>#{o.order_no}</b></Link></td><td dir="ltr">{o.total_amount} {o.currency}</td><td dir="ltr">{o.paid_amount} {o.currency}</td><td><span className="status">{fa[o.status]??o.status}</span></td><td>{new Date(o.created_at).toLocaleDateString("fa-IR")}</td><td><Link className="rowLink" href={`/panel/orders/${o.id}`}>جزئیات ←</Link></td></tr>)}{!rows.length&&<tr><td colSpan={6}><div className="emptyInset">هنوز سفارشی وجود ندارد.</div></td></tr>}</tbody></table></div></div>}

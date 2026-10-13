"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {api} from "@/lib/api";

const requestStatus:Record<string,string>={NEW:"جدید",QUALIFYING:"در حال بررسی",IN_PROGRESS:"در حال پیگیری",QUOTED:"قیمت ارسال شده",WON:"در حال اجرا",LOST:"لغو شده",CLOSED:"بسته"};
const stage:Record<string,string>={REQUESTED:"درخواست ثبت شده",PRODUCT_CREATED:"محصول شناسایی شده",SOURCING:"در حال استعلام",SUPPLIER_QUOTED:"قیمت تامین‌کننده دریافت شد",INTERNAL_COSTED:"محاسبه داخلی",CUSTOMER_QUOTED:"قیمت برای شما ارسال شد",CUSTOMER_AGREED:"توافق ثبت شد",SUPPLIER_ORDERED:"خرید از تامین‌کننده",IN_TRANSIT:"در حال حمل",CUSTOMS:"گمرک",LOCAL_WAREHOUSE:"ورود به انبار",READY_FOR_DELIVERY:"آماده تحویل",DELIVERED:"تحویل شده",CLOSED:"بسته",CANCELLED:"لغو شده"};

export default function Requests(){
  const [rows,setRows]=useState<any[]>([]),[error,setError]=useState("");
  useEffect(()=>{api.panelRequests().then(setRows).catch(e=>setError(e.message))},[]);
  return <div className="pageShell">
    <div className="pageTitleRow"><div><Link className="muted" href="/panel">← پنل</Link><h1>درخواست‌های من</h1><p>وضعیت واقعی پرونده، قیمت‌های ارسال‌شده و مراحل انجام کار را از اینجا پیگیری کنید.</p></div><Link className="primaryButton" href="/order">+ درخواست جدید</Link></div>
    {error&&<div className="errorBanner">{error}</div>}
    <div className="productTableWrap"><table className="productTable"><thead><tr><th>شماره</th><th>قطعه / شرح</th><th>مرحله فعلی</th><th>آخرین قیمت</th><th>تاریخ</th><th></th></tr></thead><tbody>
      {rows.map(r=><tr key={r.id}>
        <td>#{r.request_no}</td>
        <td><b dir="ltr">{r.requested_part_number||"—"}</b><small style={{display:"block",maxWidth:460}}>{r.product_name_fa||r.request_text}</small></td>
        <td><span className="status">{stage[r.workflow_stage]??requestStatus[r.request_status]??r.workflow_stage??r.request_status}</span></td>
        <td>{r.latest_quote_total!=null?<><b dir="ltr">{Number(r.latest_quote_total).toLocaleString()} {r.latest_quote_currency}</b>{r.latest_quote_valid_until&&<small style={{display:"block"}}>معتبر تا {new Date(r.latest_quote_valid_until).toLocaleDateString("fa-IR")}</small>}</>:<span className="muted">هنوز ارسال نشده</span>}</td>
        <td>{new Date(r.workflow_updated_at||r.updated_at||r.created_at).toLocaleDateString("fa-IR")}</td>
        <td><Link className="textButton" href={`/panel/requests/${r.id}`}>جزئیات ←</Link></td>
      </tr>)}
      {!rows.length&&<tr><td colSpan={6}><div className="emptyInset">هنوز درخواستی ثبت نشده است.</div></td></tr>}
    </tbody></table></div>
  </div>
}

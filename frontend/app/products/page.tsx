"use client";
import Link from "next/link";
import {useEffect,useMemo,useState} from "react";
import {api} from "@/lib/api";
import {Product} from "@/types/product";
import {ProductPrice} from "@/types/platform";

export default function Products(){
  const [items,setItems]=useState<Product[]>([]),[prices,setPrices]=useState<ProductPrice[]>([]),[q,setQ]=useState(""),[loading,setLoading]=useState(true);
  useEffect(()=>{Promise.all([api.products(),api.publicProductPrices()]).then(([p,pr])=>{setItems(p);setPrices(pr)}).finally(()=>setLoading(false))},[]);
  const byProduct=useMemo(()=>new Map(prices.map(p=>[p.productId,p])),[prices]);
  const list=useMemo(()=>{const s=q.trim().toLowerCase();return s?items.filter(p=>[p.productCode,p.realPartNumber,p.descriptionFa,p.descriptionEn].some(v=>v?.toLowerCase().includes(s))):items},[items,q]);
  return <div className="pageShell"><div className="pageTitle"><span className="eyebrow">کاتالوگ</span><h1>محصولات</h1><p>کد محصول، شماره فنی یا نام قطعه را جستجو کنید.</p></div><div className="toolbar"><input className="searchBox" value={q} onChange={e=>setQ(e.target.value)} placeholder="جستجوی شماره فنی، کد یا نام محصول…"/><Link className="primaryButton" href="/order">قطعه پیدا نشد؟ درخواست کنید</Link></div>{loading?<div className="loadingCard">در حال دریافت محصولات…</div>:<div className="cardGrid">{list.map(p=>{const price=byProduct.get(p.id);return <article className="contentCard" key={p.id}>{p.images[0]?.imageUrl?<img src={p.images[0].imageUrl} alt={p.descriptionFa} style={{width:"100%",height:180,objectFit:"contain",background:"#f8fafb",borderRadius:10}}/>:<div className="imagePlaceholder" style={{width:"100%",height:180}}>بدون تصویر</div>}<code style={{direction:"ltr",display:"block",color:"var(--accent)",marginTop:14}}>{p.productCode}</code><h3>{p.descriptionFa}</h3><p className="muted" dir="ltr">{p.descriptionEn}</p><p><b>شماره فنی:</b> <span dir="ltr">{p.realPartNumber}</span></p>{price?<div className="successBanner"><b dir="ltr">{Number(price.amount).toLocaleString()} {price.currency}</b><br/><small>قیمت ثبت‌شده معتبر تا {new Date(price.validUntil).toLocaleDateString("fa-IR")}</small></div>:<p className="muted">قیمت نیازمند استعلام است.</p>}<Link className="secondaryButton" href={`/order?part=${encodeURIComponent(p.realPartNumber)}&productId=${p.id}`}>{price?"درخواست / سفارش با این قیمت":"درخواست استعلام قیمت"}</Link></article>})}{!list.length&&<div className="emptyInset">محصولی پیدا نشد. می‌توانید درخواست تامین ثبت کنید.</div>}</div>}</div>
}

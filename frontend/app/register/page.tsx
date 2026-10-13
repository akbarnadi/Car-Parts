"use client";
import Link from "next/link";
import {FormEvent,useState} from "react";
import {useRouter} from "next/navigation";
import {api} from "@/lib/api";

export default function Register(){
  const router=useRouter();
  const [form,setForm]=useState({displayName:"",username:"",email:"",phone:"",password:"",confirmPassword:""});
  const [busy,setBusy]=useState(false),[error,setError]=useState("");

  async function submit(e:FormEvent){
    e.preventDefault();setBusy(true);setError("");
    if(form.password!==form.confirmPassword){setError("رمز عبور و تکرار آن یکسان نیستند.");setBusy(false);return}
    try{
      await api.register({...form,email:form.email.trim()||null});
      await api.login(form.username,form.password);
      router.push("/panel");router.refresh();
    }catch(x){setError(x instanceof Error?x.message:"ثبت‌نام انجام نشد")}finally{setBusy(false)}
  }

  return <div className="loginWrap"><div className="loginCard"><span className="eyebrow">حساب مشتری</span><h1>ساخت حساب کاربری</h1><p>شماره تماس برای بازیابی ارتباط با مشتری الزامی است. ایمیل اختیاری است.</p><form onSubmit={submit}>
    <label className="field"><span>نام و نام خانوادگی</span><input required value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})}/></label>
    <label className="field"><span>نام کاربری</span><input dir="ltr" required minLength={3} autoComplete="username" value={form.username} onChange={e=>setForm({...form,username:e.target.value})}/></label>
    <label className="field"><span>شماره تماس</span><input dir="ltr" required autoComplete="tel" value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})}/></label>
    <label className="field"><span>ایمیل (اختیاری)</span><input dir="ltr" type="email" autoComplete="email" value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label>
    <label className="field"><span>رمز عبور (حداقل ۱۲ کاراکتر)</span><input dir="ltr" type="password" minLength={12} required autoComplete="new-password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/></label>
    <label className="field"><span>تکرار رمز عبور</span><input dir="ltr" type="password" minLength={12} required autoComplete="new-password" value={form.confirmPassword} onChange={e=>setForm({...form,confirmPassword:e.target.value})}/></label>
    {error&&<div className="errorBanner">{error}</div>}
    <button className="primaryButton" disabled={busy}>{busy?"در حال ایجاد حساب…":"ایجاد حساب"}</button>
    <Link className="linkButton" href="/login">حساب دارید؟ وارد شوید</Link>
  </form></div></div>
}

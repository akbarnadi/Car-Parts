import { Lookups, Product } from "@/types/product";
import { AuthMe, LookupAdminRow, NewsPost, NotificationRow, PanelSummary, ProductPrice, ProductPriceState, WorkflowCase, WorkflowOverview, WorkflowTag } from "@/types/platform";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api";
let csrf: { headerName:string; token:string } | null = null;

async function csrfToken(){
  if(csrf)return csrf;
  const r=await fetch(`${API_URL}/auth/csrf`,{credentials:"include",cache:"no-store"});
  if(!r.ok)throw new Error("دریافت توکن امنیتی ناموفق بود.");
  csrf=await r.json();return csrf!;
}

async function request<T>(path:string,init:RequestInit={}):Promise<T>{
  const method=(init.method??"GET").toUpperCase();
  const headers:Record<string,string>={"Content-Type":"application/json",...((init.headers as Record<string,string>)??{})};
  if(!["GET","HEAD","OPTIONS"].includes(method)&&!path.startsWith("/public/")&&path!=="/auth/login"&&path!=="/auth/register"){
    const token=await csrfToken();headers[token.headerName]=token.token;
  }
  const response=await fetch(`${API_URL}${path}`,{...init,headers,credentials:"include",cache:"no-store"});
  if(response.status===401)csrf=null;
  if(!response.ok){const body=await response.json().catch(()=>null);throw new Error(body?.message??body?.detail??`خطای سرور (${response.status})`)}
  if(response.status===204||response.headers.get("content-length")==="0")return undefined as T;
  return response.json() as Promise<T>;
}

export const api={
  lookups:()=>request<Lookups>("/lookups"),
  products:()=>request<Product[]>("/products"),
  product:(id:string)=>request<Product>(`/products/${id}`),
  createProduct:(body:unknown)=>request<Product>("/products",{method:"POST",body:JSON.stringify(body)}),
  updateProduct:(id:string,body:unknown)=>request<Product>(`/products/${id}`,{method:"PUT",body:JSON.stringify(body)}),
  deleteProduct:(id:string)=>request<void>(`/products/${id}`,{method:"DELETE"}),
  codePreview:(params:Record<string,string>)=>request<{productCode:string;nextSerial:number}>(`/products/code-preview?${new URLSearchParams(params)}`),

  login:(identifier:string,password:string)=>request<AuthMe>("/auth/login",{method:"POST",body:JSON.stringify({identifier,password})}),
  register:(body:unknown)=>request<AuthMe>("/auth/register",{method:"POST",body:JSON.stringify(body)}),
  me:()=>request<AuthMe>("/auth/me"),
  logout:async()=>{await request<void>("/auth/logout",{method:"POST"});csrf=null},
  panelSummary:()=>request<PanelSummary>("/panel/summary"),
  panelOrders:()=>request<any[]>("/panel/orders"),
  panelOrder:(id:string)=>request<any>(`/panel/orders/${id}`),
  panelRequests:()=>request<any[]>("/panel/requests"),
  panelRequest:(id:string)=>request<any>(`/panel/requests/${id}`),
  notifications:(unreadOnly=false)=>request<NotificationRow[]>(`/panel/notifications?unreadOnly=${unreadOnly}`),
  readNotification:(id:string)=>request<void>(`/panel/notifications/${id}/read`,{method:"PUT"}),

  lookupRows:(type:string)=>request<LookupAdminRow[]>(`/admin/lookups/${type}`),
  createLookup:(type:string,body:unknown)=>request<LookupAdminRow>(`/admin/lookups/${type}`,{method:"POST",body:JSON.stringify(body)}),
  updateLookup:(type:string,id:string,body:unknown)=>request<LookupAdminRow>(`/admin/lookups/${type}/${id}`,{method:"PUT",body:JSON.stringify(body)}),
  deactivateLookup:(type:string,id:string)=>request<void>(`/admin/lookups/${type}/${id}`,{method:"DELETE"}),

  productCases:()=>request<any[]>("/admin/product-cases"),
  attachCaseProduct:(caseId:string,productId:string)=>request<any>(`/admin/product-cases/${caseId}/product`,{method:"PUT",body:JSON.stringify({productId})}),
  adminCases:()=>request<any[]>("/admin/operations/cases"),
  adminTags:()=>request<WorkflowTag[]>("/admin/operations/tags"),
  adminAddTag:(caseId:string,tagId:string)=>request<void>(`/admin/operations/cases/${caseId}/tags/${tagId}`,{method:"POST"}),
  adminRemoveTag:(caseId:string,tagId:string)=>request<void>(`/admin/operations/cases/${caseId}/tags/${tagId}`,{method:"DELETE"}),
  notificationOutbox:()=>request<any[]>("/admin/operations/notification-outbox"),
  markOutboxSent:(id:string)=>request<void>(`/admin/operations/notification-outbox/${id}/sent`,{method:"PUT"}),

  productPrices:(id:string)=>request<ProductPrice[]>(`/admin/product-operations/${id}/prices`),
  productPriceState:(id:string)=>request<ProductPriceState>(`/admin/product-operations/${id}/price-state`),
  setProductPrice:(id:string,body:unknown)=>request<ProductPrice>(`/admin/product-operations/${id}/prices`,{method:"POST",body:JSON.stringify(body)}),
  productHistory:(id:string)=>request<any[]>(`/admin/product-operations/${id}/history`),
  notifyProductCustomers:(id:string,body:unknown)=>request<{created:number}>(`/admin/product-operations/${id}/notify-customers`,{method:"POST",body:JSON.stringify(body)}),
  publicProductPrices:()=>request<ProductPrice[]>("/public/product-prices"),

  workflowOverview:()=>request<WorkflowOverview>("/manager/overview"),
  workflowCases:(stage?:string)=>request<WorkflowCase[]>(`/manager/cases${stage?`?stage=${encodeURIComponent(stage)}`:""}`),
  workflowCase:(id:string)=>request<any>(`/manager/cases/${id}`),
  createManualRequest:(body:unknown)=>request<WorkflowCase>("/manager/requests",{method:"POST",body:JSON.stringify(body)}),
  transitionCase:(id:string,body:unknown)=>request<WorkflowCase>(`/manager/cases/${id}/transition`,{method:"POST",body:JSON.stringify(body)}),
  workflowTags:()=>request<WorkflowTag[]>("/manager/tags"),
  createWorkflowTag:(body:unknown)=>request<any>("/manager/tags",{method:"POST",body:JSON.stringify(body)}),
  addCaseTag:(caseId:string,tagId:string)=>request<void>(`/manager/cases/${caseId}/tags/${tagId}`,{method:"POST"}),
  removeCaseTag:(caseId:string,tagId:string)=>request<void>(`/manager/cases/${caseId}/tags/${tagId}`,{method:"DELETE"}),
  addSupplierQuote:(id:string,body:unknown)=>request<any>(`/manager/cases/${id}/supplier-quotes`,{method:"POST",body:JSON.stringify(body)}),
  addCustomerQuote:(id:string,body:unknown)=>request<any>(`/manager/cases/${id}/customer-quotes`,{method:"POST",body:JSON.stringify(body)}),
  pricingWorkbook:(id:string)=>request<any>(`/manager/cases/${id}/pricing-workbook`),
  savePricingWorkbook:(id:string,body:unknown)=>request<any>(`/manager/cases/${id}/pricing-workbook`,{method:"PUT",body:JSON.stringify(body)}),
  commerce:(id:string)=>request<any>(`/manager/cases/${id}/commerce`),
  createOrder:(id:string,body:unknown)=>request<any>(`/manager/cases/${id}/order`,{method:"POST",body:JSON.stringify(body)}),
  addPayment:(orderId:string,body:unknown)=>request<any>(`/manager/orders/${orderId}/payments`,{method:"POST",body:JSON.stringify(body)}),
  createShipment:(caseId:string,body:unknown)=>request<any>(`/manager/cases/${caseId}/shipments`,{method:"POST",body:JSON.stringify(body)}),
  updateShipmentStatus:(shipmentId:string,body:unknown)=>request<any>(`/manager/shipments/${shipmentId}/status`,{method:"PUT",body:JSON.stringify(body)}),
  suppliers:()=>request<any[]>("/manager/suppliers"),
  createSupplier:(body:unknown)=>request<any>("/manager/suppliers",{method:"POST",body:JSON.stringify(body)}),
  updateSupplier:(id:string,body:unknown)=>request<any>(`/manager/suppliers/${id}`,{method:"PUT",body:JSON.stringify(body)}),
  contacts:()=>request<any[]>("/manager/contacts"),
  updateContactStatus:(id:string,status:string)=>request<void>(`/manager/contacts/${id}/status`,{method:"PUT",body:JSON.stringify({status})}),

  publicNews:()=>request<NewsPost[]>("/public/news"),
  contact:(body:unknown)=>request<any>("/public/contact",{method:"POST",body:JSON.stringify(body)}),
  publicRequest:(body:unknown)=>request<WorkflowCase>("/public/requests",{method:"POST",body:JSON.stringify(body)}),

  users:()=>request<any[]>("/admin/users"),
  createUser:(body:unknown)=>request<any>("/admin/users",{method:"POST",body:JSON.stringify(body)}),
  updateUserProfile:(id:string,body:unknown)=>request<any>(`/admin/users/${id}/profile`,{method:"PUT",body:JSON.stringify(body)}),
  updateUserRoles:(id:string,roles:string[],managerLevel?:number|null)=>request<any>(`/admin/users/${id}/roles`,{method:"PUT",body:JSON.stringify({roles,managerLevel})}),
  updateUserStatus:(id:string,status:string)=>request<any>(`/admin/users/${id}/status`,{method:"PUT",body:JSON.stringify({status})}),
  resetUserPassword:(id:string,password:string,confirmPassword:string)=>request<void>(`/admin/users/${id}/password`,{method:"PUT",body:JSON.stringify({password,confirmPassword})}),
  userHistory:(id:string)=>request<any[]>(`/admin/users/${id}/history`),

  adminNews:()=>request<any[]>("/admin/news"),
  createNews:(body:unknown)=>request<any>("/admin/news",{method:"POST",body:JSON.stringify(body)}),
  updateNews:(id:string,body:unknown)=>request<any>(`/admin/news/${id}`,{method:"PUT",body:JSON.stringify(body)})
};

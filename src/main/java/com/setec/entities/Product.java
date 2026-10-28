package com.setec.entities;

import java.util.List;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity(name = "tbl_product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String name;
	private double price;      // price in Riel, e.g. 10000
	private int qty;           // kept for backward compatibility

	// NEW: short text shown on the menu
	private String description;

	// NEW: sold-out toggle. Wrapper type so old rows (NULL) don't break.
	private Boolean available = true;

	@JsonIgnore
	private String imgUrl;

	@ManyToOne
	@JoinColumn(name = "cid")
	@JsonIgnore
	private Category category;

	// CHANGED: @JsonIgnore stops the Cart -> Product -> CartItems -> ... infinite loop
	@JsonIgnore
	@OneToMany(mappedBy = "product")
	private List<CartItems> cartItems;

	public Product() {
	}

	// Old constructor kept so existing code still compiles
	public Product(Integer id, String name, double price, int qty, String imgUrl, Category category) {
		this.id = id;
		this.name = name;
		this.price = price;
		this.qty = qty;
		this.imgUrl = imgUrl;
		this.category = category;
	}

	// ---------- computed fields in JSON ----------

	public String getFullImage() {
		return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + imgUrl;
	}

	public double getAmount() {
		return price * qty;
	}

	// NEW: the app can read the category even though the object is @JsonIgnore
	public Integer getCategoryId() {
		return category != null ? category.getId() : null;
	}

	public String getCategoryName() {
		return category != null ? category.getName() : null;
	}

	// ---------- getters / setters ----------

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getQty() {
		return qty;
	}

	public void setQty(int qty) {
		this.qty = qty;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	// NULL (old rows) is treated as available
	public Boolean getAvailable() {
		return available == null || available;
	}

	public void setAvailable(Boolean available) {
		this.available = available;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public List<CartItems> getCartItems() {
		return cartItems;
	}

	public void setCartItems(List<CartItems> cartItems) {
		this.cartItems = cartItems;
	}
}
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "tbl_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String name;
	private double price;
	private int qty;
	@JsonIgnore
	private String imgUrl;
	

	@ManyToOne
	@JoinColumn(name="cid")
	@JsonIgnore
	private Category category;
	
	@OneToMany(mappedBy = "product")
	private List<CartItems> cartItems;
	

	public Product() {
		// TODO Auto-generated constructor stub
	}

	public Product(Integer id, String name, double price, int qty, String imgUrl, Category category) {
		super();
		this.id = id;
		this.name = name;
		this.price = price;
		this.qty = qty;
		this.imgUrl = imgUrl;
		this.category = category;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public String getFullImage() {
		return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + imgUrl;
	}

	public double getAmount() {
		return price * qty;
	}

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

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
	}

}
